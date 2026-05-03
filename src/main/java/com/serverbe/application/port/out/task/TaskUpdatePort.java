package com.serverbe.application.port.out.task;


import com.serverbe.domain.model.task.AiTask;

/**
 * @responsibility AI 추론 작업(AiTask)의 상태 변경 및 신규 생성을 영속성 계층(DB)에 반영하는 아웃바운드 포트
 */
public interface TaskUpdatePort {

    /**
     * @responsibility 순수 도메인 모델(Record)의 변경된 상태를 저장하거나 갱신합니다.
     * @param aiTask 저장 또는 갱신할 대상 도메인 객체 (불변 객체이므로 변경된 새 인스턴스가 넘어옴)
     */
    AiTask save(AiTask aiTask);
}