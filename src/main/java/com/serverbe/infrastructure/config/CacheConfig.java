package com.serverbe.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Integer> localRateLimitCache() {
        return Caffeine.newBuilder()
                // 1분(또는 Rate Limit 윈도우 시간)이 지나면 메모리에서 자동 삭제
                .expireAfterWrite(1, TimeUnit.MINUTES) 
                // 최대 10,000명의 유저(IP) 정보만 메모리에 보관 (OOM 방지)
                .maximumSize(10_000) 
                .build();
    }
}