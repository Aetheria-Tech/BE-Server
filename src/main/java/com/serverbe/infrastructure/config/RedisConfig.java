package com.serverbe.infrastructure.config;

import com.serverbe.infrastructure.config.properties.RedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    private final String HOST;
    private final int PORT;

    public RedisConfig(RedisProperties redisProperties) {
        this.HOST = redisProperties.host();
        this.PORT = redisProperties.port();
    }

    /**
     * Redis 연결을 위한 ConnectionFactory 빈 등록 (Lettuce 사용)
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(HOST, PORT);
    }

    /**
     * 데이터를 저장하고 조회할 때 사용할 RedisTemplate 설정
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        // Key는 읽기 편하도록 StringSerializer 사용
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        
        // Value는 JSON 형태로 저장되도록 Jackson2JsonRedisSerializer 사용
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        // Hash 구조를 사용할 경우의 설정
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return redisTemplate;
    }
}