package org.cycle.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

/**
 * Redis 存储的会话实体。用于在 Redis 中保存每次登录的会话信息。
 * 存储键前缀为 `session`（Spring Data Redis 默认会生成类似 `session:{id}` 的 key）。
 *
 * 说明：
 * - 该实体只用于运行时会话管理（低延迟、高并发），并非用于长期审计。
 * - 如需审计，请在创建/撤销时同时写入数据库审计表。
 */
@Data
@RedisHash("session")
public class RedisSession implements Serializable {

    @Id
    private String id; // jti / session id

    /** 关联的用户 ID */
    @Indexed
    private String userId;
    private String deviceId;
    private String deviceType;
    private String ip;
    private String userAgent;

    // refresh token stored as hash
    /** 存储 refresh token 的哈希值（不保存明文） */
    private String refreshToken;

    /**
     * 刷新令牌过期时间
     */
    private Instant refreshTokenExpiresAt;
    /**
     * 会话创建时间
     */
    private Instant issuedAt;
    /**
     * 会话过期时间
     */
    private Instant expiresAt;
    /**
     * 最后活跃时间
     */
    private Instant lastSeen;

    /** 会话是否被撤销：0=未撤销，1=已撤销 */
    private Integer revoked;
}

