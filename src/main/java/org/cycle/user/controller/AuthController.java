package org.cycle.user.controller;

import org.cycle.user.entity.RoleEntity;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.mapper.RoleMapper;
import org.cycle.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.cycle.security.JwtTokenProvider;
import org.cycle.user.service.AuthService;
import org.cycle.user.service.SessionService;

import javax.servlet.http.HttpServletRequest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.util.ArrayList;
import java.util.List;

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
        public List<String> roleIds;
        // 返回给前端的用户对象（敏感字段已清理）
        public UserEntity user;

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

        // 统一使用 roleId，全程存储 roleIds，减少 code/id 混用带来的混乱
        List<String> roleIds = new ArrayList<>();
        try {
            if (roleMapper != null) {
                // 首先尝试从 Redis 缓存读取（减少 DB 访问）
                try {
                    if (redisTemplate != null) {
                        Object cached = redisTemplate.opsForValue().get("user:roleIds:" + u.getId());
                        if (cached instanceof List) {
                            //noinspection unchecked
                            roleIds = (List<String>) cached;
                        }
                    }
                } catch (Exception ignored) {}

                if (roleIds == null || roleIds.isEmpty()) {
                    // 直接查询角色实体并提取 id
                    List<RoleEntity> roles = roleMapper.selectRolesByUserId(u.getId());
                    if (roles != null) for (RoleEntity r : roles) if (r != null && r.getId() != null) roleIds.add(r.getId());
                    // 写缓存（短期缓存）
                    try {
                        if (redisTemplate != null && roleIds != null) {
                            redisTemplate.opsForValue().set("user:roleIds:" + u.getId(), roleIds, 5, java.util.concurrent.TimeUnit.MINUTES);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        // 统一在 token 中使用 roleId（而不是 role code），便于前后端全程使用 id 进行权限判定
        String access = jwtTokenProvider.createAccessToken(u.getId(), u.getUsername(), roleIds, sessionId);
        LoginResponse lr = new LoginResponse(access, refreshPlain, sessionId, jwtTokenProvider.getExpireSeconds());
        lr.roleIds = roleIds;
        try {
            if (u != null) {
                u.setPassword(null);
                u.setSalt(null);
            }
            lr.user = u;
        } catch (Exception ignored) {}
        return ResponseEntity.ok(lr);
    }

    public static class RefreshRequest {
        public String sessionId;
        public String refreshToken;
    }

    @Autowired
    private UserMapper userMapper;

    // 注入 RoleMapper 用于在生成 token 时加载角色信息
    @Autowired
    private RoleMapper roleMapper;

    @Autowired(required = false)
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        // 刷新流程：
        // 1) 校验 refresh token 与 session 中的 hash 是否匹配
        // 2) 若匹配则进行 rotate（生成新 refresh 并替换 old）
        // 3) 返回新的 access + refresh
        SessionService.RefreshResult res = sessionService.refresh(req.sessionId, req.refreshToken);
        if (res == null) return ResponseEntity.status(401).build();
        // load user by id returned from refresh
        UserEntity user = userMapper.selectById(res.userId);
        String userId = user == null ? "" : user.getId();
        String username = user == null ? "" : user.getUsername();

        // include roles on refresh as well (use role ids)
        List<String> roleIds = new ArrayList<>();
        try {
            if (roleMapper != null && userId != null && !userId.isEmpty()) {
                List<RoleEntity> roles = roleMapper.selectRolesByUserId(userId);
                if (roles != null) for (RoleEntity r : roles) if (r.getId() != null) roleIds.add(r.getId());
            }
        } catch (Exception ignored) {}

        String access = jwtTokenProvider.createAccessToken(userId, username, roleIds, req.sessionId);
        LoginResponse lr2 = new LoginResponse(access, res.refreshToken, req.sessionId, jwtTokenProvider.getExpireSeconds());
        lr2.roleIds = roleIds;
        try {
            if (user != null) {
                user.setPassword(null);
                user.setSalt(null);
            }
            lr2.user = user;
        } catch (Exception ignored) {}
        return ResponseEntity.ok(lr2);
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
