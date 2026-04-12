package com.serverbe.application.service;

import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.adapter.out.persistence.task.JpaAiTaskRepository;
import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import com.serverbe.domain.model.art.RunningArt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResultRetrievalService {

    private final JpaAiTaskRepository taskRepository;
    private final S3AiOutputPort s3AiOutputPort;
    private final RunningArtRepositoryPort runningArtRepository;

    @Transactional
    public void processTaskResult(AiTaskEntity task) {
        s3AiOutputPort.downloadOutput(task.getOutputS3Uri()).ifPresent(resultDto -> {
            log.info("[Result Retrieval] 결과물 감지 - TaskID: {}", task.getId());

            try {
                // 1. DB에 저장하고, 저장된 결과물(엔티티)을 리턴받습니다. ✨
                RunningArt savedArt = saveFinalRunningArt(task.getUserId(), resultDto);

                // 2. 리턴받은 실제 ID를 Task에 넣어줍니다. ✨
                task.markAsCompleted(savedArt.id());
                log.info("[Result Retrieval] 작업 완료 처리 성공 - TaskID: {}, 생성된 Art ID: {}", task.getId(), savedArt.id());

            } catch (Exception e) {
                log.error("[Result Retrieval Error] 데이터 저장 중 오류 - TaskID: {}", task.getId(), e);
                task.markAsFailed("데이터 저장 실패: " + e.getMessage());
            } finally {
                taskRepository.save(task);
            }
        });
    }

    /**
     * 리턴 타입을 void에서 RunningArt로 변경했습니다. ✨
     */
    private RunningArt saveFinalRunningArt(Long userId, AiGenerationResultDto dto) {

        RunningArt newArt = RunningArt.builder()
                .userId(userId)
                .startLat(dto.startLat())
                .startLon(dto.startLon())
                .distance(dto.distance())
                .gpx(dto.gpx())
                .title("AI가 그려준 런닝 코스")
                .build();

        // JPA의 save는 저장 후 ID가 발급된 영속성 객체를 반환합니다.
        RunningArt savedArt = runningArtRepository.save(newArt);

        log.info("[Result Retrieval] 사용자 {}의 런닝아트가 영구 저장되었습니다. (거리: {}m)", userId, dto.distance());

        return savedArt; // 저장된 객체 반환 ✨
    }
}