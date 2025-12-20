package com.serverbe.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

/**
 * 보안 관련 인프라 빈 설정을 담당하는 클래스입니다.
 */
@Configuration
public class SecurityBeanConfig {

    /**
     * 암호학적으로 강력한 난수 생성기인 SecureRandom을 Bean으로 등록합니다.
     * 리프레시 토큰 생성, 솔트(Salt) 생성 등 보안이 중요한 난수 발생 시 사용됩니다.
     */
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}