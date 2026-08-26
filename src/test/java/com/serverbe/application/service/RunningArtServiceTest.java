package com.serverbe.application.service;

import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.art.RunningArtRedisPort;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.exception.art.ArtErrorCode;
import com.serverbe.domain.exception.art.ArtException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.application.config.ArtSearchPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link RunningArtService}는 5개의 UseCase({@link com.serverbe.application.port.in.art.GetRunningArtUseCase},
 * {@link com.serverbe.application.port.in.art.DeleteRunningArtUseCase}, {@link com.serverbe.application.port.in.art.UpdateRunningArtUseCase},
 * {@link com.serverbe.application.port.in.art.GetNearbyRunningArtUseCase}, {@link com.serverbe.application.port.in.art.RegisterCompletedArtUseCase})를
 * 구현하는 요청 진입점이므로, 각 메서드별로 성공/실패 케이스를 모두 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RunningArtServiceTest {

    private RunningArtService runningArtService; // 테스트 대상 (진짜 객체)

    // 외부 포트들은 가짜 객체(Mock)로 주입
    @Mock
    private RunningArtRepositoryPort repositoryPort;
    @Mock
    private RunningArtRedisPort runningArtRedisPort;

    private static final Long OWNER_ID = 1L;
    private static final Long ART_ID = 100L;

    @BeforeEach
    void setUp() {
        // ArtSearchPolicy는 순수 record라 mocking하지 않고, application.yml의 실제 운영값
        // (art.max-radius, art.max-result-limit)과 동일한 값으로 실제 인스턴스를 만들어 주입합니다.
        // (RunningArtService 생성자가 값을 즉시 읽으므로 @InjectMocks로는 NPE가 납니다.)
        ArtSearchPolicy artSearchPolicy = new ArtSearchPolicy(5000.0, 5);
        runningArtService = new RunningArtService(repositoryPort, runningArtRedisPort, artSearchPolicy);
    }

    @Nested
    @DisplayName("런닝 아트 소유권 및 예외 검증 테스트")
    class OwnershipTests {

        @Test
        @DisplayName("예외: 다른 사용자의 런닝 아트를 삭제하려고 하면 예외가 발생한다.")
        void deleteRunningArt_NotOwner_ThrowsException() {
            // given (상황 설정)
            Long hackerUserId = 999L; // 삭제 요청자

            RunningArt mockArt = RunningArt.builder()
                    .id(ART_ID)
                    .userId(OWNER_ID)
                    .build();

            given(repositoryPort.findById(ART_ID)).willReturn(Optional.of(mockArt));

            // when & then (실행 및 결과 검증)
            assertThatThrownBy(() -> runningArtService.deleteRunningArt(hackerUserId, ART_ID))
                    .isInstanceOf(ArtException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ArtErrorCode.USER_IS_NOT_OWNER_OF_RUNNING_ART);

            verify(repositoryPort, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("예외: 존재하지 않는 런닝 아트를 조회하면 예외가 발생한다.")
        void getRunningArt_NotFound_ThrowsException() {
            // given
            Long invalidArtId = 999L;

            given(repositoryPort.findById(invalidArtId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> runningArtService.getRunningArtById(OWNER_ID, invalidArtId))
                    .isInstanceOf(ArtException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ArtErrorCode.NOT_FOUND_RUNNING_ART);
        }
    }

    // ================= getRunningArtById / getRunningArtsByUserId =================

    @Test
    @DisplayName("성공: 본인 소유의 런닝 아트를 단건 조회한다")
    void getRunningArtById_Success() {
        // given
        RunningArt art = RunningArt.builder().id(ART_ID).userId(OWNER_ID).title("하트 코스").build();
        given(repositoryPort.findById(ART_ID)).willReturn(Optional.of(art));

        // when
        RunningArtResult result = runningArtService.getRunningArtById(OWNER_ID, ART_ID);

        // then
        assertThat(result.id()).isEqualTo(ART_ID);
        assertThat(result.title()).isEqualTo("하트 코스");
    }

    @Test
    @DisplayName("성공: 사용자의 런닝 아트 목록을 페이지 단위로 조회한다")
    void getRunningArtsByUserId_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<RunningArt> page = new PageImpl<>(List.of(RunningArt.builder().id(ART_ID).userId(OWNER_ID).title("A").build()));
        given(repositoryPort.findByUserId(OWNER_ID, pageable)).willReturn(page);

        // when
        Page<RunningArtResult> result = runningArtService.getRunningArtsByUserId(OWNER_ID, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("A");
    }

    // ================= updateRunningArt =================

    @Test
    @DisplayName("성공: 본인 소유의 런닝 아트 메타데이터를 수정한다")
    void updateRunningArt_Success() {
        // given
        RunningArt art = RunningArt.builder().id(ART_ID).userId(OWNER_ID).build();
        given(repositoryPort.findById(ART_ID)).willReturn(Optional.of(art));
        RunningArtUpdateCommand command = new RunningArtUpdateCommand("새 제목", "새 설명");

        // when
        runningArtService.updateRunningArt(OWNER_ID, ART_ID, command);

        // then
        verify(repositoryPort).updateMetadata(ART_ID, command);
    }

    @Test
    @DisplayName("실패: 소유자가 아닌 사용자가 런닝 아트를 수정하려고 하면 예외가 발생하고 수정이 반영되지 않는다")
    void updateRunningArt_Fail_NotOwner() {
        // given
        Long hackerUserId = 999L;
        RunningArt art = RunningArt.builder().id(ART_ID).userId(OWNER_ID).build();
        given(repositoryPort.findById(ART_ID)).willReturn(Optional.of(art));
        RunningArtUpdateCommand command = new RunningArtUpdateCommand("새 제목", "새 설명");

        // when & then
        assertThatThrownBy(() -> runningArtService.updateRunningArt(hackerUserId, ART_ID, command))
                .isInstanceOf(ArtException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArtErrorCode.USER_IS_NOT_OWNER_OF_RUNNING_ART);

        verify(repositoryPort, never()).updateMetadata(anyLong(), any());
    }

    // ================= deleteRunningArt =================

    @Test
    @DisplayName("성공: 본인 소유의 런닝 아트를 삭제하면 DB 삭제 후 트랜잭션 커밋 시점에 Redis GEO 데이터도 정리된다")
    void deleteRunningArt_Success() {
        // given
        RunningArt art = RunningArt.builder().id(ART_ID).userId(OWNER_ID).build();
        given(repositoryPort.findById(ART_ID)).willReturn(Optional.of(art));
        given(runningArtRedisPort.removeLocation(ART_ID)).willReturn(Mono.just(1L));

        // 이 서비스는 트랜잭션 커밋 이후 콜백에서 Redis 정리를 수행하므로, 테스트에서 직접
        // 트랜잭션 동기화 컨텍스트를 열어주고 커밋을 흉내내어 콜백을 수동으로 실행시켜야 한다.
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            runningArtService.deleteRunningArt(OWNER_ID, ART_ID);

            // then: DB 삭제는 즉시 반영됨
            verify(repositoryPort).deleteById(ART_ID);

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            // 커밋 이후 콜백을 수동으로 실행 (실제 트랜잭션 매니저 역할을 대신함)
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Redis 정리는 커밋 콜백이 실행된 이후에만 호출되어야 한다
        verify(runningArtRedisPort).removeLocation(ART_ID);
    }

    // ================= getNearbyArts =================

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

    @Test
    @DisplayName("실패: 요청 반경이 최대 허용치를 초과하면 INVALID_RADIUS 예외가 발생하고 Redis 조회는 시도되지 않는다")
    void getNearbyArts_Fail_RadiusExceedsMax() {
        // when & then: setUp에서 maxRadius=5000.0으로 설정했으므로 6000.0은 초과값
        StepVerifier.create(runningArtService.getNearbyArts(37.0, 127.0, 6000.0))
                .expectErrorMatches(e -> e instanceof ArtException ae && ae.getErrorCode() == ArtErrorCode.INVALID_RADIUS)
                .verify();

        verify(runningArtRedisPort, never()).findNearbyIds(any(), any(), any());
    }

    // ================= registerFromPolyline =================

    @Test
    @DisplayName("성공: 유효한 폴리라인을 등록하면 메타데이터를 추출해 DB/Redis에 저장하고 생성된 ID를 반환한다")
    void registerFromPolyline_Success() {
        // given: 구글 Encoded Polyline 알고리즘의 표준 예시 문자열 (3개 좌표 포함)
        String validPolyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
        Long savedArtId = 777L;
        given(repositoryPort.save(any(RunningArt.class))).willAnswer(invocation -> {
            RunningArt art = invocation.getArgument(0);
            return art.toBuilder().id(savedArtId).build();
        });
        given(runningArtRedisPort.saveLocation(eq(savedArtId), any(), any())).willReturn(Mono.just(1L));

        // when
        Long resultId = runningArtService.registerFromPolyline(OWNER_ID, validPolyline, "AI 생성 코스", "HEART", Proficiency.BEGINNER);

        // then
        assertThat(resultId).isEqualTo(savedArtId);
        verify(runningArtRedisPort).saveLocation(eq(savedArtId), any(), any());
    }

    @Test
    @DisplayName("실패: 빈 폴리라인 문자열을 등록하려고 하면 파싱 예외가 발생하고 DB에는 아무것도 저장되지 않는다")
    void registerFromPolyline_Fail_EmptyPolyline() {
        // when & then
        assertThatThrownBy(() -> runningArtService.registerFromPolyline(OWNER_ID, "", "AI 생성 코스", "HEART", Proficiency.BEGINNER))
                .isInstanceOf(ServerException.class)
                .hasFieldOrPropertyWithValue("errorCode", ServerErrorCode.POLYLINE_PARSE_ERROR);

        verify(repositoryPort, never()).save(any());
        verify(runningArtRedisPort, never()).saveLocation(any(), any(), any());
    }
}
