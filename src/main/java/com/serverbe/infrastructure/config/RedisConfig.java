package com.serverbe.infrastructure.config;

import com.serverbe.infrastructure.config.properties.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @responsibility <b>Redis</b> 데이터베이스와의 연결을 설정하고, 데이터 접근을 위한 템플릿을 구성합니다.
 * @implSpec {@link RedisProperties}로부터 접속 정보를 주입받아 <b>Lettuce</b> 기반의 연결 팩토리를 생성하며, 데이터 가독성을 위해 <b>JSON 직렬화</b>를 적용합니다.
 */
@Configuration
public class RedisConfig {
    private final String HOST;
    private final int PORT;

    /**
     * @param redisProperties Redis 접속 설정 정보
     * @responsibility 설정 프로퍼티 객체인 {@link RedisProperties}로부터 호스트와 포트 정보를 추출하여 초기화합니다.
     */
    public RedisConfig(RedisProperties redisProperties) {
        this.HOST = redisProperties.host();
        this.PORT = redisProperties.port();
    }

    /**
     * @return {@link RedisConnectionFactory} 인터페이스의 Lettuce 구현체
     * @responsibility 비동기 및 논블로킹 라이브러리인 <b>Lettuce</b>를 사용하여 Redis 연결 팩토리를 생성합니다.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(HOST, PORT);
    }

    /**
     * @return 설정이 완료된 {@link RedisTemplate} 인스턴스
     * @responsibility Redis 데이터를 조작하기 위한 상위 수준의 추상화 인터페이스인 {@link RedisTemplate}을 설정합니다.
     * @implNote 1. <b>Key Serializer</b>: 키는 관리의 편의를 위해 <b>String</b> 직렬화를 사용합니다.<br>
     * 2. <b>Value Serializer</b>: 객체를 <b>JSON</b> 형태로 저장하여 별도의 클래스 타입 정보 없이도 범용적인 조회가 가능하도록 {@link GenericJackson2JsonRedisSerializer}를 사용합니다.
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


    /**
     * @responsibility WebFlux 환경에서 논블로킹으로 Redis GEO 및 캐시 작업을 수행하기 위한 템플릿입니다.
     * @implNote Key와 Value 모두 String으로 직렬화하여 GEO 검색 시 ID(String) 처리를 최적화합니다.
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(RedisConnectionFactory factory) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(serializer)
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();
        return new ReactiveRedisTemplate<>((ReactiveRedisConnectionFactory) factory, context);
    }
}