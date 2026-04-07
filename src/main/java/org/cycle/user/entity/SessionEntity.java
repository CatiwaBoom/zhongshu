package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;
import java.time.Instant;

/**
 * Session 实体映射到 sys_session
 * 用于记录每次登录会话信息，便于支持踢人、单浏览器登录、刷新令牌等功能
 */
@Data
@TableName("sys_session")
public class SessionEntity extends BaseEntity implements Serializable {
    /** 关联用户ID */
    private String userId;

    /** 设备 ID，前端生成并存储在 localStorage，区分不同浏览器 */
    private String deviceId;

    /** 设备类型，例如 BROWSER/MOBILE */
    private String deviceType;

    /** 登录时的 IP 地址 */
    private String ip;

    /** User-Agent 字符串 */
    private String userAgent;

    /** JWT 的 jti 字段，等于 session id */
    private String jti;

    /** 存储 refresh token 的哈希值，避免保存明文 */
    private String refreshToken; // stored as hash

    /** refresh token 过期时间 */
    private Instant refreshTokenExpiresAt;

    /** access token 签发时间 */
    private Instant issuedAt;

    /** access token 过期时间（冗余，可由 JWT exp 决定） */
    private Instant expiresAt;

    /** 最后活动时间（用于会话列表或在线状态） */
    private Instant lastSeen;

    /** 是否被撤销：0=未撤销，1=已撤销 */
    private Integer revoked; // 0/1
}


