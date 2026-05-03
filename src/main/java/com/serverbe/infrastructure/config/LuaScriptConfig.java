package com.serverbe.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis Lua 스크립트들을 Bean으로 등록하여 재사용하기 위한 설정 클래스입니다.
 * Lua 스크립트는 Redis 서버 측에서 원자성(Atomicity)을 보장하며 실행됩니다.
 */
@Configuration
public class LuaScriptConfig {

    /**
     * 처리율 제한(Rate Limiting)을 위한 토큰 버킷 알고리즘 스크립트.
     */
    @Bean
    public RedisScript<Boolean> rateLimitScript() {
        return RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), Boolean.class);
    }

    /**
     * 신규 리프레시 토큰 저장 및 세션 인덱스(ZSet) 업데이트 스크립트.
     * 기기 제한(Max Tokens) 로직을 포함합니다.
     */
    @Bean
    public RedisScript<Boolean> saveTokenScript() {
        return RedisScript.of(new ClassPathResource("scripts/save_token.lua"), Boolean.class);
    }

    /**
     * Refresh Token Rotation (RTR)을 수행하는 스크립트.
     * 기존 토큰을 블랙리스트에 추가하고 새로운 토큰을 발급하여 저장합니다.
     */
    @Bean
    public RedisScript<Boolean> rotateTokenScript() {
        return RedisScript.of(new ClassPathResource("scripts/rotate_token.lua"), Boolean.class);
    }

    /**
     * 사용자의 모든 기기 세션을 만료시키는 전역 로그아웃 스크립트.
     */
    @Bean
    public RedisScript<Boolean> globalLogoutScript() {
        return RedisScript.of(new ClassPathResource("scripts/global_logout.lua"), Boolean.class);
    }

    /**
     * 특정 기기의 리프레시 토큰만 삭제하는 스크립트.
     */
    @Bean
    public RedisScript<Boolean> deleteTokenScript() {
        return RedisScript.of(new ClassPathResource("scripts/delete_token.lua"), Boolean.class);
    }
}