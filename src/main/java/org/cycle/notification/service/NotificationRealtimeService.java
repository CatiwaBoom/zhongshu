package org.cycle.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.notification.dto.NotificationPushEvent;
import org.cycle.security.JwtTokenProvider;
import org.cycle.user.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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
public class NotificationRealtimeService {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitterPool = new ConcurrentHashMap<>();

    // 使用单线程定时器发送心跳，避免连接长时间空闲被网关关闭
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "notification-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(this::broadcastHeartbeat, 20, 20, TimeUnit.SECONDS);
    }

    public String resolveUserIdByToken(String token) {
        // EventSource 无法携带 Authorization 头，这里通过 URL token 解析用户并校验 session 有效性
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            Jws<Claims> jws = jwtTokenProvider.parseToken(token);
            Claims claims = jws.getBody();
            String userId = claims.getSubject();
            String sessionId = claims.getId();
            if (sessionId == null || !sessionService.isSessionValid(sessionId)) {
                return null;
            }
            return userId;
        } catch (ExpiredJwtException ex) {
            // access token 过期时，允许使用 claims 中的 sessionId 继续校验会话有效性。
            // 这样在 refresh 周期内，SSE 不会因为 access 令牌到期立刻中断并触发“登录失效”体感。
            Claims claims = ex.getClaims();
            if (claims == null) {
                return null;
            }
            String userId = claims.getSubject();
            String sessionId = claims.getId();
            if (sessionId == null || !sessionService.isSessionValid(sessionId)) {
                return null;
            }
            return userId;
        } catch (Exception e) {
            log.warn("解析SSE令牌失败", e);
            return null;
        }
    }

    public SseEmitter createEmitter(String userId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(0L);
        emitterPool.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        // 连接结束、超时、异常时都要移除，避免连接池内存泄漏
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("{\"unreadCount\":" + unreadCount + "}"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    public void push(NotificationPushEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        List<SseEmitter> emitters = emitterPool.get(event.getUserId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("站内信推送序列化失败", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notice").data(json));
            } catch (Exception e) {
                removeEmitter(event.getUserId(), emitter);
            }
        }
    }

    private void broadcastHeartbeat() {
        // 向全部连接发送轻量心跳，防止反向代理把长连接当作空闲连接回收
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
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emitterPool.remove(userId);
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }
}
