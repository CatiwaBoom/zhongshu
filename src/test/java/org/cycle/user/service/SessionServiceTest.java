package org.cycle.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class SessionServiceTest {

    private SessionService sessionService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        sessionService = new SessionService();
        sessionService.setRedisTemplate(redisTemplate);
    }

    @Test
    public void testStoreAndGetRefreshToken() {
        String refresh = "r1";
        String sessionUuid = "s1";
        when(valueOps.get("refresh:" + refresh)).thenReturn(sessionUuid);
        sessionService.storeRefreshToken(refresh, sessionUuid, 3600);
        // verify set called
        verify(valueOps).set(eq("refresh:" + refresh), eq((Object)sessionUuid), org.mockito.ArgumentMatchers.any());
        String got = sessionService.getSessionUuidByRefreshToken(refresh);
        assertEquals(sessionUuid, got);
    }

    @Test
    public void testRotateRefreshToken() {
        String oldR = "old";
        String newR = "new";
        String sessionUuid = "s2";
        when(valueOps.get("refresh:" + oldR)).thenReturn(sessionUuid);
        // perform rotate
        boolean ok = sessionService.rotateRefreshToken(oldR, newR, sessionUuid, 3600);
        assertTrue(ok);
        verify(redisTemplate).delete(eq("refresh:" + oldR));
        verify(valueOps).set(eq("refresh:" + newR), eq((Object)sessionUuid), org.mockito.ArgumentMatchers.any());
    }
}

