package org.cycle.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// ...existing code...
import org.cycle.security.TokenUtil;
import org.cycle.user.entity.SessionEntity;
import org.cycle.user.mapper.SessionMapper;
import org.cycle.user.service.SessionService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class SessionServiceImpl extends SessionService {

    @Autowired
    private SessionMapper sessionMapper;

    private static final long ACCESS_TOKEN_SECONDS = 21600; // 6h
    private static final long REFRESH_TOKEN_SECONDS = 7 * 24 * 3600; // 7 days

    @Override
    @Transactional
    public String createSession(String userId, String deviceId, String deviceType, String ip, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        String refreshPlain = TokenUtil.generateToken(64);
        String refreshHash = TokenUtil.sha256(refreshPlain);

        SessionEntity s = new SessionEntity();
        s.setId(sessionId);
        s.setUserId(userId);
        s.setDeviceId(deviceId);
        s.setDeviceType(deviceType);
        s.setIp(ip);
        s.setUserAgent(userAgent);
        s.setJti(sessionId);
        s.setIssuedAt(Instant.now());
        s.setExpiresAt(Instant.now().plusSeconds(ACCESS_TOKEN_SECONDS));
        s.setRefreshToken(refreshHash);
        s.setRefreshTokenExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_SECONDS));
        s.setRevoked(0);
        sessionMapper.insert(s);

        //给该用户其他的会话设置撤销
        UpdateWrapper<SessionEntity> uw = new UpdateWrapper<>();
        // 对同一 userId、排除当前 sessionId 的记录进行更新，
        // 并且仅更新当前未撤销的会话（revoked = 0 或 revoked IS NULL）
        uw.eq("user_id", userId)
                .ne("id", sessionId)
                .and(w -> w.eq("revoked", 0).or().isNull("revoked"));

        SessionEntity upd = new SessionEntity();
        upd.setRevoked(1);
        upd.setRefreshToken(null);
        upd.setRefreshTokenExpiresAt(Instant.now());

        sessionMapper.update(upd, uw);


        // 3) 返回 sessionId 与 refresh token 明文（controller 应安全传输给客户端）
        return sessionId + "|" + refreshPlain;
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        SessionEntity s = sessionMapper.selectById(sessionId);
        if (s == null) return false;
        if (s.getRevoked() != null && s.getRevoked() == 1) return false;
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(Instant.now())) return false;
        return true;
    }

    @Override
    @Transactional
    public void revokeSession(String sessionId, String reason, String operatorId) {
        SessionEntity s = sessionMapper.selectById(sessionId);
        if (s == null) return;
        // 标记为已撤销，并应当写入审计日志、黑名单并推送通知
        s.setRevoked(1);
        sessionMapper.updateById(s);
        // TODO: write audit log and push notification (WebSocket / PubSub)
    }

    @Override
    @Transactional
    public RefreshResult refresh(String sessionId, String refreshToken) {
        SessionEntity s = sessionMapper.selectById(sessionId);
        if (s == null) return null;
        if (s.getRevoked() != null && s.getRevoked() == 1) return null;
        if (s.getRefreshTokenExpiresAt() != null && s.getRefreshTokenExpiresAt().isBefore(Instant.now())) return null;
        String providedHash = TokenUtil.sha256(refreshToken);
        if (!providedHash.equals(s.getRefreshToken())) {
            // possible replay or theft — revoke
            // 若 refresh token 与存储 hash 不匹配，视为重放攻击或被盗用，撤销会话并报警
            s.setRevoked(1);
            sessionMapper.updateById(s);
            // TODO: 写审计日志并触发安全告警
            return null;
        }

        // rotate refresh token
        String newRefreshPlain = TokenUtil.generateToken(64);
        String newRefreshHash = TokenUtil.sha256(newRefreshPlain);
        s.setRefreshToken(newRefreshHash);
        s.setRefreshTokenExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_SECONDS));
        s.setIssuedAt(Instant.now());
        s.setExpiresAt(Instant.now().plusSeconds(ACCESS_TOKEN_SECONDS));
        sessionMapper.updateById(s);

        // 返回新的 refresh token 明文与 userId，controller 将基于 userId 生成新的 access token
        RefreshResult res = new RefreshResult();
        res.refreshToken = newRefreshPlain;
        res.userId = s.getUserId();
        return res;
    }
}







