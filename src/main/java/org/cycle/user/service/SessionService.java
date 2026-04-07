package org.cycle.user.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

/**
 * Abstract SessionService providing Redis helpers used by tests and
 * abstract methods for session lifecycle to be implemented by concrete classes.
 */
public class SessionService {

    protected RedisTemplate<String, Object> redisTemplate;

    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeRefreshToken(String refreshToken, String sessionUuid, int seconds) {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        ops.set("refresh:" + refreshToken, sessionUuid, seconds, TimeUnit.SECONDS);
    }

    public String getSessionUuidByRefreshToken(String refreshToken) {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        Object v = ops.get("refresh:" + refreshToken);
        return v == null ? null : v.toString();
    }

    public boolean rotateRefreshToken(String oldRefresh, String newRefresh, String sessionUuid, int seconds) {
        redisTemplate.delete("refresh:" + oldRefresh);
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        ops.set("refresh:" + newRefresh, sessionUuid, seconds, TimeUnit.SECONDS);
        return true;
    }
    /**
     * 创建会话
     * 实现应当：
     * - 撤销（标记）旧会话（如果采用单活策略）
     * - 生成 sessionId, 存储 refreshToken 的 hash
     * - 返回 sessionId 与明文 refresh token（明文只返回一次）
     */
    public String createSession(String userId, String deviceId, String deviceType, String ip, String userAgent) {
        throw new UnsupportedOperationException("Not implemented in base SessionService");
    }

    /**
     * 判断 session 是否有效（未被撤销且未过期）
     */
    public boolean isSessionValid(String sessionId) {
        throw new UnsupportedOperationException("Not implemented in base SessionService");
    }

    /**
     * 撤销会话（踢人）
     * 应写入审计日志并在必要时将 jti 加入黑名单
     */
    public void revokeSession(String sessionId, String reason, String operatorId) {
        throw new UnsupportedOperationException("Not implemented in base SessionService");
    }

    /**
     * 使用 refresh token 执行刷新并进行 rotation
     * 返回新的 refresh token 明文以及关联 userId
     */
    public RefreshResult refresh(String sessionId, String refreshToken) {
        throw new UnsupportedOperationException("Not implemented in base SessionService");
    }

    public static class RefreshResult {
        public String refreshToken;
        public String userId;
    }
}





