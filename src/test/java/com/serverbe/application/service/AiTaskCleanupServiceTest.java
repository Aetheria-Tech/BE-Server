package com.serverbe.application.service;

import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;
import com.serverbe.infrastructure.config.properties.TaskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AiTaskCleanupService}는 스케줄러가 주기적으로 호출하는
 * {@link com.serverbe.application.port.in.task.CleanupZombieTaskUseCase}의 구현체입니다.
 */
@ExtendWith(MockitoExtension.class)
class AiTaskCleanupServiceTest {

    @Mock
    private TaskQueryPort taskQueryPort;
    @Mock
    private TaskUpdatePort taskUpdatePort;
    @Mock
    private com.serverbe.application.service.helper.AiTaskResourceCleaner resourceCleaner;

    private AiTaskCleanupService aiTaskCleanupService;

    @BeforeEach
    void setUp() {
        aiTaskCleanupService = new AiTaskCleanupService(taskQueryPort, taskUpdatePort, resourceCleaner, new TaskProperties(10));
    }

    private AiTask zombieTask(String id) {
        LocalDateTime old = LocalDateTime.now().minusMinutes(30);
        return new AiTask(id, 1L, "HEART", Proficiency.BEGINNER, TaskStatus.PROCESSING, "s3://in", null, null, old, old, null);
    }

    @Test
    @DisplayName("성공: 좀비 Task가 존재하면 모두 FAILED 상태로 갱신하여 저장한다")
    void cleanUpZombieTasks_Success_MarksAllAsFailed() {
        // given
        List<AiTask> zombies = List.of(zombieTask("zombie-1"), zombieTask("zombie-2"));
        given(taskQueryPort.findZombieTasks(anyList(), any(LocalDateTime.class))).willReturn(zombies);
        given(taskUpdatePort.save(any(AiTask.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        aiTaskCleanupService.cleanUpZombieTasks();

        // then
        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskUpdatePort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AiTask::status)
                .containsOnly(TaskStatus.FAILED);
        assertThat(captor.getAllValues())
                .extracting(AiTask::errorMessage)
                .allMatch(msg -> msg.contains("Timeout"));
    }

    @Test
    @DisplayName("성공(무처리): 좀비 Task가 하나도 없으면 저장 로직을 전혀 호출하지 않는다")
    void cleanUpZombieTasks_NoZombies_DoesNothing() {
        // given
        given(taskQueryPort.findZombieTasks(anyList(), any(LocalDateTime.class))).willReturn(List.of());

        // when
        aiTaskCleanupService.cleanUpZombieTasks();

        // then
        verify(taskUpdatePort, never()).save(any());
        verify(resourceCleaner, never()).cleanUp(any(AiTask.class));
    }

    @Test
    @DisplayName("실패: 저장 중 예외가 발생하면 삼키지 않고 그대로 전파하여 @Transactional 롤백을 유도한다")
    void cleanUpZombieTasks_Fail_SaveThrows_PropagatesException() {
        // given
        given(taskQueryPort.findZombieTasks(anyList(), any(LocalDateTime.class))).willReturn(List.of(zombieTask("zombie-1")));
        given(taskUpdatePort.save(any(AiTask.class))).willThrow(new RuntimeException("DB 커넥션 끊김"));

        // when & then
        assertThatThrownBy(() -> aiTaskCleanupService.cleanUpZombieTasks())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 커넥션 끊김");
    }

    @Test
    @DisplayName("비용 방어: 좀비 Task를 FAILED 처리할 때 S3에 방치된 임시 자원도 함께 정리한다")
    void cleanUpZombieTasks_AlsoCleansUpS3Resources() {
        // given: 좀비 작업은 "AI 서버가 끝내 응답하지 않아 입력 프롬프트가 S3에 방치된" 바로 그 상황이다.
        // 상태만 FAILED로 바꾸고 파일을 두면 타임아웃될수록 스토리지 비용이 누적된다.
        List<AiTask> zombies = List.of(zombieTask("zombie-1"), zombieTask("zombie-2"));
        given(taskQueryPort.findZombieTasks(anyList(), any(LocalDateTime.class))).willReturn(zombies);
        given(taskUpdatePort.save(any(AiTask.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        aiTaskCleanupService.cleanUpZombieTasks();

        // then
        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(resourceCleaner, times(2)).cleanUp(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(task -> assertThat(task.status()).isEqualTo(TaskStatus.FAILED));
    }
}
