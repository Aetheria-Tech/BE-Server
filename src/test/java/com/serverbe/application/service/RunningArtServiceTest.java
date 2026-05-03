package com.serverbe.application.service;

import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.out.art.RunningArtRedisPort;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.exception.art.ArtErrorCode;
import com.serverbe.domain.exception.art.ArtException;
import com.serverbe.domain.model.art.RunningArt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunningArtServiceTest {

    @InjectMocks
    private RunningArtService runningArtService; // 테스트 대상 (진짜 객체)

    // 외부 포트들은 가짜 객체(Mock)로 주입
    @Mock
    private RunningArtRepositoryPort repositoryPort;
    @Mock
    private RunningArtRedisPort runningArtRedisPort;

    @Nested
    @DisplayName("런닝 아트 소유권 및 예외 검증 테스트")
    class OwnershipTests {

        @Test
        @DisplayName("예외: 다른 사용자의 런닝 아트를 삭제하려고 하면 예외가 발생한다.")
        void deleteRunningArt_NotOwner_ThrowsException() {
            // given (상황 설정)
            Long hackerUserId = 999L; // 삭제 요청자
            Long ownerUserId = 1L;    // 실제 작성자
            Long targetArtId = 100L;

            RunningArt mockArt = RunningArt.builder()
                    .id(targetArtId)
                    .userId(ownerUserId)
                    .build();

            given(repositoryPort.findById(targetArtId)).willReturn(Optional.of(mockArt));

            // when & then (실행 및 결과 검증)
            assertThatThrownBy(() -> runningArtService.deleteRunningArt(hackerUserId, targetArtId))
                    .isInstanceOf(ArtException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ArtErrorCode.USER_IS_NOT_OWNER_OF_RUNNING_ART);
        }

        @Test
        @DisplayName("예외: 존재하지 않는 런닝 아트를 조회하면 예외가 발생한다.")
        void getRunningArt_NotFound_ThrowsException() {
            // given
            Long userId = 1L;
            Long invalidArtId = 999L;

            given(repositoryPort.findById(invalidArtId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> runningArtService.getRunningArtById(userId, invalidArtId))
                    .isInstanceOf(ArtException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ArtErrorCode.NOT_FOUND_RUNNING_ART);
        }
    }

    @Test
    @DisplayName("성공: 주변 런닝 아트 검색 시, Redis 조회 후 DB 조회가 이어서 실행된다.")
    void getNearbyArts_Success() {
        // given
        List<Long> mockRedisIds = List.of(1L, 2L);
        List<RunningArt> mockDbResults = List.of(
                RunningArt.builder().id(1L).title("아트1").build(),
                RunningArt.builder().id(2L).title("아트2").build()
        );

        // Redis에서는 반경 내의 ID(1, 2)를 스트리밍
        given(runningArtRedisPort.findNearbyIds(37.0, 127.0, 5.0))
                .willReturn(Flux.fromIterable(mockRedisIds));

        // DB에서는 ID 리스트를 받아 상세 정보를 반환
        given(repositoryPort.findAllByIdIn(mockRedisIds))
                .willReturn(mockDbResults);

        // when
        Flux<RunningArtResult> resultFlux = runningArtService.getNearbyArts(37.0, 127.0, 5.0);

        // then
        StepVerifier.create(resultFlux)
                .expectNextMatches(result -> result.id().equals(1L))
                .expectNextMatches(result -> result.id().equals(2L))
                .verifyComplete();

        verify(repositoryPort, times(1)).findAllByIdIn(mockRedisIds);
    }
}