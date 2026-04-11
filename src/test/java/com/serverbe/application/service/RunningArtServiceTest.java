package com.serverbe.application.service;

import com.serverbe.adapter.in.web.dto.geocode.GeocodeResponse;
import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.out.ai.RunningArtAIPort;
import com.serverbe.application.port.out.ai.RunningArtRedisPort;
import com.serverbe.application.port.out.dto.ai.RunningArtAiResponse;
import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.exception.art.ArtErrorCode;
import com.serverbe.domain.exception.art.ArtException;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.model.art.vo.Proficiency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunningArtServiceTest {

    @InjectMocks
    private RunningArtService runningArtService; // 테스트 대상 (진짜 객체)

    // 외부 포트들은 가짜 객체(Mock)로 주입
    @Mock private RunningArtRepositoryPort repositoryPort;
    @Mock private RunningArtAIPort runningArtAIPort;
    @Mock private GeocodePort geocodePort;
    @Mock private RunningArtRedisPort runningArtRedisPort;

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

    @Nested
    @DisplayName("리액티브(비동기) 파이프라인 검증 테스트")
    class ReactivePipelineTests {

        @Test
        @DisplayName("성공: 런닝 아트가 생성되며, AI -> DB -> Redis 파이프라인이 정상 동작한다.")
        void createRunningArt_Success() {
            // given
            Long userId = 1L;
            String mockGpx = "q`jdFub_fW@B?BAD"; // 디코딩 시 [37.0, 127.0] 근처라고 가정

            // 1. Geocode Mocking
            given(geocodePort.geocode("용인 아르피아"))
                    .willReturn(Mono.just(new GeocodeResult(37.323, 127.106, "asd")));

            // 2. AI Port Mocking
            given(runningArtAIPort.createRunningArtGPX(37.323, 127.106, "강아지", Proficiency.MASTER))
                    .willReturn(Mono.just(new RunningArtAiResponse(mockGpx)));

            // 3. DB Save Mocking (저장 시 ID 10L 할당)
            given(repositoryPort.save(any(RunningArt.class))).willAnswer(invocation -> {
                RunningArt art = invocation.getArgument(0);
                return art.toBuilder().id(10L).build();
            });

            // 4. Redis Save Mocking
            given(runningArtRedisPort.saveLocation(any(), any(), any()))
                    .willReturn(Mono.just(1L));

            // when
            Mono<RunningArtResult> resultMono = runningArtService.createRunningArt(
                    userId, "용인 아르피아", "강아지", Proficiency.MASTER
            );

            // then: StepVerifier를 사용한 비동기 스트림 검증
            StepVerifier.create(resultMono)
                    .assertNext(result -> {
                        assertThat(result.id()).isEqualTo(10L); // DB에서 부여한 ID 확인
                        assertThat(result.userId()).isEqualTo(userId);
                        assertThat(result.shape()).isEqualTo("강아지");
                        assertThat(result.proficiency()).isEqualTo(Proficiency.MASTER);
                    })
                    .verifyComplete();

            // 부수 효과(Side-Effect) 검증: DB와 Redis에 실제로 데이터를 꽂으려고 시도했는지 확인
            verify(repositoryPort, times(1)).save(any(RunningArt.class));
            verify(runningArtRedisPort, times(1)).saveLocation(any(), any(), any());
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
}