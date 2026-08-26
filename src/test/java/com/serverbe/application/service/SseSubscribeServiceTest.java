package com.serverbe.application.service;

import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.domain.model.task.AiTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

/**
 * {@link SseSubscribeService}는 {@link com.serverbe.application.port.in.notification.SseSubscribeUseCase}의 구현체입니다.
 */
@ExtendWith(MockitoExtension.class)
class SseSubscribeServiceTest {

    @Mock
    private TaskNotificationPort taskNotificationPort;
    @Mock
    private TaskQueryPort taskQueryPort;

    private SseSubscribeService sseSubscribeService;

    private static final String TASK_ID = "task-abc";
    private static final Long OWNER_ID = 1L;

    @BeforeEach
    void setUp() {
        sseSubscribeService = new SseSubscribeService(taskNotificationPort, taskQueryPort);
    }

    @Test
    @DisplayName("성공: 본인이 요청한 Task를 구독하면 SseEmitter를 반환한다")
    void subscribe_Success() {
        // given
        AiTask task = AiTask.createPending(OWNER_ID, "HEART", Proficiency.BEGINNER);
        given(taskQueryPort.findById(TASK_ID)).willReturn(Optional.of(task));
        SseEmitter emitter = new SseEmitter();
        given(taskNotificationPort.subscribe(TASK_ID)).willReturn(emitter);

        // when
        SseEmitter result = sseSubscribeService.subscribe(OWNER_ID, TASK_ID);

        // then
        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("실패: 존재하지 않는 Task를 구독하려고 하면 NOT_FOUND_AITASK 예외가 발생한다")
    void subscribe_Fail_NotFound() {
        // given
        given(taskQueryPort.findById(TASK_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sseSubscribeService.subscribe(OWNER_ID, TASK_ID))
                .isInstanceOf(AiException.class)
                .hasFieldOrPropertyWithValue("errorCode", AiErrorCode.NOT_FOUND_AITASK);

        org.mockito.Mockito.verify(taskNotificationPort, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("실패: 타인의 Task를 구독하려고 하면 USER_IS_NOT_OWNER_OF_TASK 예외가 발생한다")
    void subscribe_Fail_NotOwner() {
        // given
        Long strangerId = 999L;
        AiTask task = AiTask.createPending(OWNER_ID, "HEART", Proficiency.BEGINNER);
        given(taskQueryPort.findById(TASK_ID)).willReturn(Optional.of(task));

        // when & then
        assertThatThrownBy(() -> sseSubscribeService.subscribe(strangerId, TASK_ID))
                .isInstanceOf(AiException.class)
                .hasFieldOrPropertyWithValue("errorCode", AiErrorCode.USER_IS_NOT_OWNER_OF_TASK);

        org.mockito.Mockito.verify(taskNotificationPort, never()).subscribe(anyString());
    }
}
