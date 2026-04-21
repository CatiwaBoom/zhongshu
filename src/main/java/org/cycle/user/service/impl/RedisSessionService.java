package org.cycle.user.service.impl;

import org.cycle.security.TokenUtil;
import org.cycle.user.entity.RedisSession;
import org.cycle.user.repository.RedisSessionRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.cycle.user.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;

/**
 * 基于 Redis 的会话服务实现。
 *
 * 说明：
 * - 会话以 `session:{id}` 为 key 存储，值为序列化后的 RedisSession 对象（由 Spring Data Redis 管理）。
 * - 每次登录会创建一个新的会话（sessionId）并返回明文 refresh token（明文只返回一次）。
 * - 支持单活策略：在创建新会话时尝试撤销该用户的其它会话（优先使用派生查询，回退到索引或扫描），
 *   以兼容遗留或直接通过 RedisTemplate 写入的会话数据。
 * - 支持滑动过期：在验证 JWT 的过滤器中会调用 touchSession 更新 lastSeen 与 expiresAt，从而延长会话有效期。
 */
@Service
@Primary
public class RedisSessionService extends SessionService {

    @Autowired
    private RedisSessionRepository redisSessionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 访问令牌过期时间（秒）——从配置中注入，与 JwtTokenProvider 保持一致
    @Value("${security.jwt.expire-seconds:3600}")
    private long accessTokenSeconds;
    private static final long REFRESH_TOKEN_SECONDS = 7 * 24 * 3600; // 7 天（刷新令牌有效期）

    @Override
    @Transactional
    public String createSession(String userId, String deviceId, String deviceType, String ip, String userAgent) {
        // 方法说明：
        // - 生成 sessionId 与 refresh token（明文与 hash）
        // - 尝试撤销/过期该用户的其它会话（实现单活策略）
        // - 将会话写入 Redis，并在 sessions:user:{userId} 中维护索引
        // 返回：sessionId + "|" + refreshPlain（调用方需解析并将 refreshPlain 明文安全保存到客户端）

        // 注意：这里对旧会话的处理采用温和的撤销策略，尽量兼容在 Redis 中直接写入的历史数据。
        String sessionId = UUID.randomUUID().toString();
        // 单活策略：在创建新会话前，撤销该用户的其它会话
        try {
            List<RedisSession> existing = redisSessionRepository.findAllByUserId(userId);
            // 如果通过 repository 派生查询没有返回（可能因为数据是用 RedisTemplate 直接写入的，未创建索引），
            // 那么回退到我们维护的 Set 索引（sessions:user:{userId}），从中读取 sessionId 列表并加载实体。
            if (existing == null || existing.isEmpty()) {
                try {
                    Set<Object> members = redisTemplate.opsForSet().members("sessions:user:" + userId);
                    boolean handled = false;
                    if (members != null && !members.isEmpty()) {
                        handled = true;
                        for (Object o : members) {
                            if (o == null) continue;
                            String id = o.toString();
                            try {
                                Optional<RedisSession> optional = redisSessionRepository.findById(id);
                                if (optional.isPresent()) {
                                    RedisSession old = optional.get();
                                    if (old.getRevoked() != null && old.getRevoked() == 1) continue;
                                    try {
                                        String oldKey = "session:" + old.getId();
                                        redisTemplate.delete(oldKey);
                                    } catch (Exception ignored) {
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    // fallback: 如果没有 set 索引，则扫描所有 session:* 键并逐个加载对比 userId
                    // 这是为了兼容历史遗留数据：某些会话可能是通过 RedisTemplate 直接写入，缺少索引
                    if (!handled) {
                        try {
                            Set<String> keys = redisTemplate.keys("session:*");
                            if (keys != null && !keys.isEmpty()) {
                                for (String key : keys) {
                                    if (key == null) continue;
                                    String id = key.startsWith("session:") ? key.substring(8) : key;
                                    try {
                                        Optional<RedisSession> optional = redisSessionRepository.findById(id);
                                        if (optional.isPresent()) {
                                            RedisSession old = optional.get();
                                            if (old.getUserId() != null && old.getUserId().equals(userId)) {
                                                if (old.getRevoked() != null && old.getRevoked() == 1) continue;
                                                try {
                                                    redisTemplate.delete(key);
                                                } catch (Exception ignored) {
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            } else {
                for (RedisSession old : existing) {
                    if (old == null) continue;
                    // 跳过已经是已撤销或与即将创建的 sessionId 相同（理论上不会相同）
                    if (old.getRevoked() != null && old.getRevoked() == 1) continue;
                    try {
                        String oldKey = "session:" + old.getId();
                        redisTemplate.expire(oldKey, 60, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String refreshPlain = TokenUtil.generateToken(64);
        String refreshHash = TokenUtil.sha256(refreshPlain);

        RedisSession s = new RedisSession();
        s.setId(sessionId);
        s.setUserId(userId);
        s.setDeviceId(deviceId);
        s.setDeviceType(deviceType);
        s.setIp(ip);
        s.setUserAgent(userAgent);
        s.setRefreshToken(refreshHash);
        s.setIssuedAt(Instant.now());
        s.setExpiresAt(Instant.now().plusSeconds(accessTokenSeconds));
        s.setRefreshTokenExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_SECONDS));
        s.setRevoked(0);

        // 保存到 Redis
        redisSessionRepository.save(s);
        // 维护一个基于 userId 的 Set 索引，便于在无法通过派生查询找到记录时回退查询
        try {
            redisTemplate.opsForSet().add("sessions:user:" + userId, sessionId);
        } catch (Exception ignored) {
        }

        // 为该会话的 Redis key 设置 TTL，确保会话在 refresh 到期后自动失效
        try {
            String key = "session:" + sessionId;
            redisTemplate.expire(key, REFRESH_TOKEN_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }


        return sessionId + "|" + refreshPlain;
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        Optional<RedisSession> opt = redisSessionRepository.findById(sessionId);
        if (!opt.isPresent()) return false;
        RedisSession s = opt.get();
        // 检查是否被撤销或过期
        if (s.getRevoked() != null && s.getRevoked() == 1) return false;
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(Instant.now())) return false;
        return true;
    }

    /**
     * 更新会话的活动时间（滑动过期），并延长该 session 在 Redis 中的 TTL。
     *
     * 触发点：通常在 JwtAuthenticationFilter 中每次请求验证成功后调用。
     * 副作用：更新 lastSeen、expiresAt，并在 Redis 中延长 key 的 TTL（以 refresh TTL 为准）。
     * @param sessionId 会话 id
     */
    public void touchSession(String sessionId) {
        try {
            Optional<RedisSession> opt = redisSessionRepository.findById(sessionId);
            if (!opt.isPresent()) return;
            RedisSession s = opt.get();
            if (s.getRevoked() != null && s.getRevoked() == 1) return;
            s.setLastSeen(Instant.now());
            s.setExpiresAt(Instant.now().plusSeconds(accessTokenSeconds));
            redisSessionRepository.save(s);
            try {
                String key = "session:" + sessionId;
                redisTemplate.expire(key, REFRESH_TOKEN_SECONDS, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public void revokeSession(String sessionId, String reason, String operatorId) {
        Optional<RedisSession> opt = redisSessionRepository.findById(sessionId);
        if (!opt.isPresent()) return;
        RedisSession s = opt.get();
        // 将会话标记为已撤销（可结合消息推送或审计日志实现即时下线）
        s.setRevoked(1);
        redisSessionRepository.save(s);
        // 从基于 userId 的 Set 索引中移除
        try {
            if (s.getUserId() != null) {
                redisTemplate.opsForSet().remove("sessions:user:" + s.getUserId(), sessionId);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public RefreshResult refresh(String sessionId, String refreshToken) {
        Optional<RedisSession> opt = redisSessionRepository.findById(sessionId);
        if (!opt.isPresent()) return null;
        RedisSession s = opt.get();
        // 验证会话状态与 refresh token
        if (s.getRevoked() != null && s.getRevoked() == 1) return null;
        if (s.getRefreshTokenExpiresAt() != null && s.getRefreshTokenExpiresAt().isBefore(Instant.now())) return null;
        String providedHash = TokenUtil.sha256(refreshToken);
        if (!providedHash.equals(s.getRefreshToken())) {
            // refresh token 不匹配：可能为重放或窃取，直接撤销会话以保证安全
            s.setRevoked(1);
            redisSessionRepository.save(s);
            return null;
        }

        // 通过验证：对 refresh token 进行 rotation（生成新的明文并保存其 hash）
        String newRefreshPlain = TokenUtil.generateToken(64);
        String newRefreshHash = TokenUtil.sha256(newRefreshPlain);
        s.setRefreshToken(newRefreshHash);
        s.setRefreshTokenExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_SECONDS));
        // 更新 issuedAt/expiresAt/lastSeen，使 access token 与滑动过期保持一致
        s.setIssuedAt(Instant.now());
        s.setExpiresAt(Instant.now().plusSeconds(accessTokenSeconds));
        s.setLastSeen(Instant.now());
        redisSessionRepository.save(s);
        try {
            String key = "session:" + sessionId;
            redisTemplate.expire(key, REFRESH_TOKEN_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }

        RefreshResult res = new RefreshResult();
        res.refreshToken = newRefreshPlain;
        res.userId = s.getUserId();
        return res;
    }
}

