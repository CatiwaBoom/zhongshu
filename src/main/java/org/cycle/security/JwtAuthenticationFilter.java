package org.cycle.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.cycle.user.service.SessionService;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SessionService sessionService) {
        this.tokenProvider = tokenProvider;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (tokenProvider.validateToken(token)) {
                Jws<Claims> claimsJws = tokenProvider.parseToken(token);
                Claims claims = claimsJws.getBody();
                String userId = claims.getSubject();
                String jti = claims.getId();
                // 从 JWT claims 中读取角色列表（可能为原生 List，需要转换为 String 类型列表）
                List<String> roles = claims.get("roles", List.class);

                // 校验 session 并触发滑动过期：
                // 1) 校验 session 是否有效（未被踢、未过期）
                // 2) 若有效则调用 touchSession 更新 lastSeen/expiresAt（延长会话有效期）
                if (sessionService != null && sessionService.isSessionValid(jti)) {
                    try {
                        sessionService.touchSession(jti);
                    } catch (Exception ignored) {
                    }
                    // 2) 基于 JWT 中的 roles 构建 Spring Security Authentication
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            roles == null ? Collections.emptyList() : roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                    );
                    // 3) 将 Authentication 放入 SecurityContext 供下游使用
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    // session 已撤销或过期，返回 401
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}



