package org.cycle.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuRealtimeService {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitterPool = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "menu-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(this::broadcastHeartbeat, 20, 20, TimeUnit.SECONDS);
    }

    public String resolveUserIdByToken(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        try {
            Jws<Claims> jws = jwtTokenProvider.parseToken(token);
            Claims claims = jws.getBody();
            String userId = claims.getSubject();
            String sessionId = claims.getId();
            if (sessionId == null || !sessionService.isSessionValid(sessionId)) return null;
            return userId;
        } catch (ExpiredJwtException ex) {
            Claims claims = ex.getClaims();
            if (claims == null) return null;
            String userId = claims.getSubject();
            String sessionId = claims.getId();
            if (sessionId == null || !sessionService.isSessionValid(sessionId)) return null;
            return userId;
        } catch (Exception e) {
            log.warn("解析SSE令牌失败", e);
            return null;
        }
    }

    public SseEmitter createEmitter(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitterPool.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("init").data("{}"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    public void pushMenuUpdate(String userId, Object payload) {
        if (userId == null) return;
        List<SseEmitter> emitters = emitterPool.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        String json;
        try { json = objectMapper.writeValueAsString(payload); } catch (Exception e) { log.error("菜单推送序列化失败", e); return; }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("menu").data(json));
            } catch (Exception e) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void broadcastHeartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emitterPool.entrySet()) {
            String userId = entry.getKey();
            List<SseEmitter> emitters = entry.getValue();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("ping"));
                } catch (Exception e) {
                    removeEmitter(userId, emitter);
                }
            }
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterPool.get(userId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) emitterPool.remove(userId);
    }

    @PreDestroy
    public void shutdown() { heartbeatExecutor.shutdownNow(); }
}

