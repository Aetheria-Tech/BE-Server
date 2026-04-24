package com.serverbe.infrastructure.config;

import com.serverbe.adapter.out.notification.SseRedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * @responsibility 다중 서버 환경에서 SSE 알림 전파를 위한 Redis Pub/Sub 메시징 설정을 담당합니다.
 */
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    private final SseRedisSubscriber sseRedisSubscriber;

    @Bean
    public ChannelTopic sseTopic() {
        return new ChannelTopic("sse-notifications");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            // 💡 RedisConfig에서 생성한 ConnectionFactory를 여기서 자연스럽게 주입받아 사용합니다!
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(sseRedisSubscriber, sseTopic());

        return container;
    }
}