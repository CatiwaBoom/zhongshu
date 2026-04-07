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
     * @param roles
     * @param sessionId
     * @return
     */
    public String createAccessToken(String userId, String username, List<String> roles, String sessionId) {
        // 生成 JWT access token
        // claims:
        // - sub: userId
        // - username
        // - roles
        // - jti: sessionId
        // 注意：生产环境请使用 RS256 并妥善管理私钥
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
     * @param token
     * @return
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
}




