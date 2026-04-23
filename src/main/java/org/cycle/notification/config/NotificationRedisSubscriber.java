package org.cycle.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.notification.dto.NotificationPushEvent;
import org.cycle.notification.service.NotificationRealtimeService;
import org.cycle.notification.service.impl.NotificationServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationRedisSubscriber {

    private final ObjectMapper objectMapper;
    private final NotificationRealtimeService notificationRealtimeService;

    @Bean
    public RedisMessageListenerContainer notificationRedisListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody());
                NotificationPushEvent event = objectMapper.readValue(payload, NotificationPushEvent.class);
                notificationRealtimeService.push(event);
            } catch (Exception e) {
                log.error("消费站内信Redis消息失败", e);
            }
        };

        container.addMessageListener(listener, new ChannelTopic(NotificationServiceImpl.REDIS_CHANNEL));
        return container;
    }
}

