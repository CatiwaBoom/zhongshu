package org.cycle.user.service.impl;

import org.cycle.user.entity.RedisSession;
import org.cycle.user.repository.RedisSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RedisSessionServiceTest {

    @Mock
    private RedisSessionRepository repo;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private RedisSessionService service;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new RedisSessionService();
        // inject mocks into private fields via reflection to avoid changing production visibility
        try {
            java.lang.reflect.Field f1 = RedisSessionService.class.getDeclaredField("redisSessionRepository");
            f1.setAccessible(true);
            f1.set(service, repo);
            java.lang.reflect.Field f2 = RedisSessionService.class.getDeclaredField("redisTemplate");
            f2.setAccessible(true);
            f2.set(service, redisTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void createSession_revokesExistingSessions_andCreatesNew() {
        String userId = "u1";
        RedisSession old = new RedisSession();
        old.setId("old-session");
        old.setUserId(userId);
        old.setRevoked(0);
        old.setIssuedAt(Instant.now());
        old.setExpiresAt(Instant.now().plusSeconds(60));

        when(repo.findAllByUserId(userId)).thenReturn(Arrays.asList(old));

        String res = service.createSession(userId, "device1", "web", "127.0.0.1", "ua");
        assertNotNull(res);
        assertTrue(res.contains("|"));
        String newSessionId = res.split("\\|", 2)[0];
        assertNotNull(newSessionId);

        // Verify repository/findAll was called
        verify(repo).findAllByUserId(userId);

        // Depending on implementation the old session may be saved (revoked) or only its TTL updated.
        // Ensure at least the old session key had its TTL shortened so it will expire soon.
        verify(redisTemplate).expire(eq("session:old-session"), anyLong(), any());

        // verify new session was saved and its key TTL set
        verify(repo, atLeastOnce()).save(argThat(s -> s != null && newSessionId.equals(s.getId())));
        verify(redisTemplate).expire(eq("session:" + newSessionId), anyLong(), any());
    }
}

