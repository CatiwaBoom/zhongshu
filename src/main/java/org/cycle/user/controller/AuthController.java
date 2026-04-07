package org.cycle.user.controller;

import org.cycle.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.cycle.security.JwtTokenProvider;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.service.AuthService;
import org.cycle.user.service.SessionService;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public static class LoginRequest {
        public String username;
        public String password;
        public String deviceId;
    }

    /**
     * 登录响应
     */
    public static class LoginResponse {
        /**
         *
         */
        public String accessToken;
        public String refreshToken;
        public String sessionId;
        public long expiresIn;
        public LoginResponse(String accessToken, String refreshToken, String sessionId, long expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.sessionId = sessionId;
            this.expiresIn = expiresIn;
        }
    }

    /**
     * 登录
     * @param req
     * @param request
     * @return
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        // 登录流程：
        // 1) 校验用户名/密码
        // 2) 调用 SessionService.createSession 创建会话（单活策略会撤销旧会话）
        // 3) 生成 access token 并返回 access + refresh (refresh 明文只返回一次)
        UserEntity u = authService.authenticate(req.username, req.password);
        if (u == null) return ResponseEntity.status(401).build();
        String composite = sessionService.createSession(u.getId(), req.deviceId, "BROWSER", request.getRemoteAddr(), request.getHeader("User-Agent"));
        String[] parts = composite.split("\\|", 2);
        String sessionId = parts[0];
        String refreshPlain = parts.length > 1 ? parts[1] : null;
        String access = jwtTokenProvider.createAccessToken(u.getId(), u.getUsername(), Collections.emptyList(), sessionId);
        return ResponseEntity.ok(new LoginResponse(access, refreshPlain, sessionId, 21600));
    }

    public static class RefreshRequest {
        public String sessionId;
        public String refreshToken;
    }

    @Autowired
    private UserMapper userMapper;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        // 刷新流程：
        // 1) 校验 refresh token 与 session 中的 hash 是否匹配
        // 2) 若匹配则进行 rotate（生成新 refresh 并替换 old）
        // 3) 返回新的 access + refresh
        SessionService.RefreshResult res = sessionService.refresh(req.sessionId, req.refreshToken);
        if (res == null) return ResponseEntity.status(401).build();
        // load user by id returned from refresh
        org.cycle.user.entity.UserEntity user = userMapper.selectById(res.userId);
        String userId = user == null ? "" : user.getId();
        String username = user == null ? "" : user.getUsername();
        String access = jwtTokenProvider.createAccessToken(userId, username, Collections.emptyList(), req.sessionId);
        return ResponseEntity.ok(new LoginResponse(access, res.refreshToken, req.sessionId, 21600));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String auth) {
        // 登出：撤销当前 token 对应的 session
        if (auth == null || !auth.startsWith("Bearer ")) return ResponseEntity.ok().build();
        String token = auth.substring(7);
        Jws<Claims> jws = jwtTokenProvider.parseToken(token);
        Claims claims = jws.getBody();
        String jti = claims.getId();
        sessionService.revokeSession(jti, "user_logout", null);
        return ResponseEntity.ok().build();
    }
}




