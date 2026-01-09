package com.serverbe.infrastructure.config;

import com.serverbe.infrastructure.config.converter.StringToOAuthProviderConverter;
import com.serverbe.infrastructure.config.properties.CorsProperties;
import com.serverbe.infrastructure.crypto.EncryptionContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @responsibility Spring MVC의 전역 설정을 담당하며, <b>CORS 정책, 데이터 변환기(Converter), 인터셉터</b>를 시스템에 등록합니다.
 * @implSpec {@link WebMvcConfigurer} 인터페이스를 구현하여 프레임워크의 기본 동작을 확장합니다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;
    private final EncryptionContextInterceptor encryptionContextInterceptor;

    /**
     * @param registry CORS 정책을 관리하는 {@link CorsRegistry}
     * @responsibility 브라우저 환경에서 발생할 수 있는 <b>CORS(Cross-Origin Resource Sharing)</b> 이슈를 해결하기 위한 정책을 정의합니다.
     * @implNote 1. <b>도메인 제어</b>: {@link CorsProperties}에 정의된 신뢰할 수 있는 도메인에 대해서만 접근을 허용합니다.<br>
     * 2. <b>인증 허용</b>: {@code allowCredentials(true)} 설정을 통해 브라우저 간 <b>쿠키 공유 및 Authorization 헤더</b> 전달이 가능하도록 합니다.<br>
     * 3. <b>메서드 허용</b>: RESTful API 구현에 필요한 주요 HTTP Method(GET, POST, PUT, DELETE, PATCH)를 모두 허용합니다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0])) // 리스트를 배열로 변환하여 주입
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // 쿠키 공유를 위해 필수
                .maxAge(3600);
    }

    /**
     * @param registry 포맷터 및 컨버터를 관리하는 {@link FormatterRegistry}
     * @responsibility HTTP 요청 파라미터나 경로 변수를 객체로 바인딩할 때 필요한 <b>커스텀 컨버터</b>를 등록합니다.
     * @implNote 문자열로 들어오는 OAuth 공급자 정보를 {@link com.serverbe.domain.model.user.vo.OAuthProvider} Enum으로 변환하기 위해 {@link StringToOAuthProviderConverter}를 등록합니다.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToOAuthProviderConverter());
    }

    /**
     * @param registry 인터셉터 체인을 관리하는 {@link InterceptorRegistry}
     * @responsibility 컨트롤러 실행 전후에 공통 로직을 수행할 <b>인터셉터</b>를 등록합니다.
     * @implNote 요청 스레드별 암호화 문맥을 관리하는 {@link EncryptionContextInterceptor}를 핸들러 매핑에 추가합니다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(encryptionContextInterceptor);
    }
}