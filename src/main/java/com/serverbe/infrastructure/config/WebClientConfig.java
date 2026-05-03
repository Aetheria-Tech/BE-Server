package com.serverbe.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @responsibility 외부 시스템과의 <b>Non-blocking 리액티브 통신</b>을 위한 {@link WebClient}의 기본 설정을 담당합니다.
 * @implSpec <b>Reactor Netty</b>의 {@link HttpClient}를 커스텀하여 연결, 응답, 읽기/쓰기 전 과정에 대한 타임아웃 정책을 일관되게 적용합니다.
 */
@Configuration
public class WebClientConfig {

    /**
     * @return 타임아웃 핸들러가 구성된 {@link WebClient.Builder}
     * @responsibility 타임아웃 정책이 사전 설정된 {@link WebClient.Builder}를 빈으로 등록합니다.
     * @implNote <b>[적용된 타임아웃 정책]</b> <br>
     * 1. <b>Connect Timeout</b>: 서버와 TCP 연결을 맺는 시간 제한 (5초)<br>
     * 2. <b>Response Timeout</b>: 요청 후 첫 응답이 올 때까지의 시간 제한 (5초)<br>
     * 3. <b>Read/Write Timeout</b>: 데이터 패킷을 읽거나 쓰는 개별 I/O 작업 시간 제한 (5초)
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // HttpClient 설정을 통해 상세 타임아웃 제어
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 연결 타임아웃 (기본값)
                .responseTimeout(Duration.ofSeconds(5)) // 응답 타임아웃
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}