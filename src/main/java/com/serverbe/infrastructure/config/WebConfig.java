package com.serverbe.infrastructure.config;

import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.config.converter.StringToOAuthProviderConverter;
import com.serverbe.infrastructure.config.properties.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.core.convert.converter.Converter;

/**
 * 브라우저 환경에서 쿠키를 포함한 Cross-Origin 요청을 허용하기 위한 설정입니다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0])) // 리스트를 배열로 변환하여 주입
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // 쿠키 공유를 위해 필수
                .maxAge(3600);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToOAuthProviderConverter());
    }
}