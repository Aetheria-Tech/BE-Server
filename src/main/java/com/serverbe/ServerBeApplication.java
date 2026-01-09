package com.serverbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * @responsibility 애플리케이션의 진입점이며, <b>Semi-Reactive</b> 아키텍처 설정을 활성화합니다.
 * @implNote <b>[Architecture: Semi-Reactive]</b>
 * 본 프로젝트는 <b>Spring MVC (Servlet Stack)</b>를 기본 프레임워크로 채택하되,
 * 외부 API 통신 및 특정 비동기 작업에 <b>Project Reactor (Reactive Stack)</b>를 부분적으로 결합한
 * 'Semi-Reactive' 모델을 지향합니다.
 * <ul>
 * <li><b>Servlet Stack:</b> HTTP 요청 처리, 보안(SecurityContext), 트랜잭션 관리의 안정성을 보장합니다.</li>
 * <li><b>Reactive Stack:</b> {@link org.springframework.web.reactive.function.client.WebClient}를 사용하여
 * 외부 서비스(카카오, 구글 등) 호출 시 I/O 블로킹을 최소화하고 리소스 효율을 극대화합니다.</li>
 * </ul>
 * @implSpec {@link ConfigurationPropertiesScan}을 통해 {@code infrastructure/config/properties} 계층의
 * Immutable한 설정 객체들을 자동으로 스캔하여 빈으로 등록합니다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ServerBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerBeApplication.class, args);
    }

}