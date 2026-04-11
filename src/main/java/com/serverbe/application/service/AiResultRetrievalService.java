package com.serverbe.application.service;

import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.adapter.out.persistence.task.JpaAiTaskRepository;
import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.infrastructure.aws.S3AiOutputAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResultRetrievalService {

    private final JpaAiTaskRepository taskRepository;
    private final S3AiOutputAdapter s3OutputAdapter;
    private final RunningArtRepositoryPort runningArtRepository;

    @Transactional
    public void processTaskResult(AiTaskEntity task) {
        s3OutputAdapter.downloadOutput(task.getOutputS3Uri()).ifPresent(resultDto -> {
            log.info("[Result Retrieval] 결과물 감지 - TaskID: {}", task.getId());

            try {
                // 1. 최종 DB 저장 로직 호출
                saveFinalRunningArt(task.getUserId(), resultDto);

                // 2. Task 상태 완료 처리 (중복 제거 완료)
                task.markAsCompleted(task.getOutputS3Uri());
                log.info("[Result Retrieval] 작업 완료 처리 성공 - TaskID: {}", task.getId());

            } catch (Exception e) {
                log.error("[Result Retrieval Error] 데이터 저장 중 오류 - TaskID: {}", task.getId(), e);
                task.markAsFailed("데이터 저장 실패: " + e.getMessage());
            }
        });
    }

    /**
     * AI 결과를 바탕으로 도메인 객체를 생성하고 DB에 영구 저장합니다.
     */
    private void saveFinalRunningArt(Long userId, AiGenerationResultDto dto) {

        // 1. DTO 데이터를 바탕으로 RunningArt 도메인(또는 엔티티) 객체 조립
        RunningArt newArt = RunningArt.builder()
                .userId(userId)
                .startLat(dto.startLat())    // 시작 위도
                .startLon(dto.startLon())    // 시작 경도
                .distance(dto.distance())    // 총 거리
                .polyline(dto.gpx())         // 압축된 폴리라인 문자열 (gpx)
                // 필요하다면 기본 제목 등 다른 필드도 여기서 세팅
                // .title("AI가 그려준 런닝 코스")
                .build();

        // 2. 레포지토리 포트를 통해 DB에 저장 (JPA의 save 호출)
        runningArtRepository.save(newArt);

        log.info("[Result Retrieval] 사용자 {}의 런닝아트가 영구 저장되었습니다. (거리: {}m)", userId, dto.distance());
    }
}