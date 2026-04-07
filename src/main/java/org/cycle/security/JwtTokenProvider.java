package org.cycle.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.secret:defaultsecretdefaultsecretdefault}")
    private String secret;

    @Value("${security.jwt.expire-seconds:21600}")
    private long expireSeconds;


    /**
     * 创建访问令牌
     * @param userId 用户id
     * @param username 用户名
     * @param roles 用户角色列表
     * @param sessionId 会话 id（jti），用于与后端 session 关联
     * @return 生成的 JWT access token
     */
    public String createAccessToken(String userId, String username, List<String> roles, String sessionId) {
        // 生成 JWT access token
        // Claims 内容说明：
        // - sub: 用户 ID
        // - username: 用户名
        // - roles: 角色列表
        // - jti: sessionId（用于将 JWT 与后端会话关联）
        // 注意：生产环境应优先使用非对称算法（如 RS256）并妥善管理私钥/公钥对
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .claim("roles", roles)
                .setId(sessionId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS512, secret.getBytes())
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        // 解析并验证 JWT，返回 Jws<Claims>
        return Jwts.parser().setSigningKey(secret.getBytes()).parseClaimsJws(token);
    }

    /**
     * 验证Token
     * @param token 要校验的 JWT 字符串
     * @return 校验结果：true 表示 token 有效且未过期，false 表示无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception ex) {
            // token 无效或过期
            return false;
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}




