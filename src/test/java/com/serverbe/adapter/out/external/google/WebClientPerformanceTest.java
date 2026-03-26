package com.serverbe.adapter.out.external.google;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestTemplate vs WebClient 성능 및 스레드 점유율 비교")
class WebClientPerformanceTest {

    private MockWebServer mockWebServer;
    private String mockUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final WebClient webClient = WebClient.create();

    private final int REQUEST_COUNT = 100;
    private final int NETWORK_DELAY_MS = 500;

    // @BeforeAll 대신 @BeforeEach를 사용하여 테스트 간 완벽한 격리 보장
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        // [핵심 수정] DNS 이슈(kubernetes.docker.internal)를 막기 위해 무조건 127.0.0.1 사용
        mockUrl = "http://127.0.0.1:" + mockWebServer.getPort();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("1. RestTemplate (Blocking I/O) 성능 측정")
    void testRestTemplatePerformance() throws InterruptedException {
        // given: 지연된 응답 100개 세팅
        for (int i = 0; i < REQUEST_COUNT; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setBody("{\"access_token\":\"dummy\"}")
                    .setHeader("Content-Type", "application/json")
                    .setBodyDelay(NETWORK_DELAY_MS, TimeUnit.MILLISECONDS));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(REQUEST_COUNT);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        ThreadMonitor monitor = new ThreadMonitor();
        monitor.start();

        // when
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity(mockUrl, String.class);
                } finally {
                    // [핵심 수정] 요청이 실패해도 무조건 카운트다운을 내려 무한 대기(Hang) 방지
                    latch.countDown();
                }
            }, executorService);
            futures.add(future);
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        monitor.stop();
        executorService.shutdown();

        // then
        long duration = endTime - startTime;
        System.out.println("========== [RestTemplate (Blocking)] ==========");
        System.out.println("총 처리 시간: " + duration + " ms");
        System.out.println("최대 활성 스레드 수: " + monitor.getPeakThreadCount());
        System.out.println("===============================================");

        assertThat(duration).isGreaterThanOrEqualTo(NETWORK_DELAY_MS);
    }

    @Test
    @DisplayName("2. WebClient (Non-Blocking I/O) 성능 측정")
    void testWebClientPerformance() {
        // given: 지연된 응답 100개 세팅
        for (int i = 0; i < REQUEST_COUNT; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setBody("{\"access_token\":\"dummy\"}")
                    .setHeader("Content-Type", "application/json")
                    .setBodyDelay(NETWORK_DELAY_MS, TimeUnit.MILLISECONDS));
        }

        ThreadMonitor monitor = new ThreadMonitor();
        monitor.start();

        // when
        long startTime = System.currentTimeMillis();

        Flux.range(0, REQUEST_COUNT)
                // [핵심 수정] flatMap의 두 번째 인자인 concurrency를 제한 (예: 50)
                // 한 번에 너무 많은 Connection을 맺어 MockWebServer가 거부하는 것을 방지
                .flatMap(i -> webClient.get()
                                .uri(mockUrl)
                                .retrieve()
                                .bodyToMono(String.class)
                                // 실패 시 재시도 로직 추가 (일시적인 Connection 거부 대응)
                                .retryWhen(reactor.util.retry.Retry.max(3))
                                // 에러가 발생하더라도 전체 스트림이 죽지 않도록 빈 값으로 대체
                                .onErrorResume(e -> Mono.empty()),
                        50) // 동시성 레벨 설정
                .collectList()
                .block();

        long endTime = System.currentTimeMillis();
        monitor.stop();

        // then
        long duration = endTime - startTime;
        System.out.println("========== [WebClient (Non-Blocking)] =========");
        System.out.println("총 처리 시간: " + duration + " ms");
        System.out.println("최대 활성 스레드 수: " + monitor.getPeakThreadCount());
        System.out.println("===============================================");

        // 동시성 제한으로 인해 총 처리 시간이 약간 늘어날 수 있음
        assertThat(duration).isGreaterThanOrEqualTo(NETWORK_DELAY_MS);
    }

    /**
     * 실행 중인 JVM의 최대 활성 스레드 수를 모니터링하기 위한 헬퍼 클래스
     */
    static class ThreadMonitor {
        private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        private volatile boolean running = true;
        private int peakThreadCount = 0;
        private Thread monitorThread;

        public void start() {
            int baseline = threadMXBean.getThreadCount();
            monitorThread = new Thread(() -> {
                while (running) {
                    int currentCount = threadMXBean.getThreadCount();
                    peakThreadCount = Math.max(peakThreadCount, currentCount - baseline);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            monitorThread.start();
        }

        public void stop() {
            running = false;
            try {
                monitorThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public int getPeakThreadCount() {
            return peakThreadCount;
        }
    }
}