package com.serverbe.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class LuaScriptConfig {
    @Bean
    public RedisScript<Boolean> rateLimitScript() {
        return RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), Boolean.class);
    }

    @Bean
    public RedisScript<Boolean> saveTokenScript() {
        return RedisScript.of(new ClassPathResource("scripts/save_token.lua"), Boolean.class);
    }

    @Bean
    public RedisScript<Boolean> rotateTokenScript() {
        return RedisScript.of(new ClassPathResource("scripts/rotate_token.lua"), Boolean.class);
    }

    @Bean
    public RedisScript<Boolean> globalLogoutScript() {
        return RedisScript.of(new ClassPathResource("scripts/global_logout.lua"), Boolean.class);
    }

    @Bean
    public RedisScript<Boolean> deleteTokenScript() {
        return RedisScript.of(new ClassPathResource("scripts/delete_token.lua"), Boolean.class);
    }
}