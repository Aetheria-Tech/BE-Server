package com.serverbe.application.service;

import com.serverbe.application.port.in.dto.art.RunningArtLocationDto;
import com.serverbe.application.port.out.ai.RunningArtRedisPort;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 서버 시작 시 MySQL의 공간 데이터를 Redis GEO로 동기화하는 배치 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGeoWarmUpListener {

    private final RunningArtRepositoryPort repositoryPort;
    private final RunningArtRedisPort redisPort;

    @EventListener(ApplicationReadyEvent.class)
    public void syncGeoDataOnStartup() {
        log.info("[Redis Warm-up] MySQL -> Redis GEO 데이터 동기화를 시작합니다.");

        // 1. 기존 Redis 데이터 초기화 (유령 데이터 방지)
        redisPort.clearAllLocations().block(); // 초기화 배치이므로 block() 허용
        log.info("기존 Redis GEO 데이터 초기화 완료");

        // 2. MySQL에서 위치 데이터만 고속 로딩
        List<RunningArtLocationDto> locations = repositoryPort.findAllLocations();
        
        if (locations.isEmpty()) {
            log.info("동기화할 런닝 아트 데이터가 없습니다.");
            return;
        }

        // 3. Redis에 일괄 삽입 (비동기 스트림 처리)
        log.info("총 {}건의 데이터를 Redis에 적재합니다...", locations.size());
        
        Flux.fromIterable(locations)
                .flatMap(dto -> redisPort.saveLocation(dto.id(), dto.startLat(), dto.startLon()))
                // 에러가 나더라도 스트림이 끊기지 않고 다음 데이터를 처리하도록 에러 로깅만 진행
                .doOnError(e -> log.error("Redis 적재 중 오류 발생: {}", e.getMessage()))
                .onErrorContinue((e, obj) -> {}) 
                .count() // 성공적으로 처리된 개수 세기
                .subscribe(
                        count -> log.info("[Redis Warm-up] 완료! 총 {}건 동기화 성공", count),
                        error -> log.error("[Redis Warm-up] 치명적 오류로 동기화 실패: {}", error.getMessage())
                );
    }
}