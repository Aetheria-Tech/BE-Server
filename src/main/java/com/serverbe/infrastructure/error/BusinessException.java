package com.serverbe.infrastructure.error;

import lombok.Getter;

/**
 * @responsibility 애플리케이션 내 비즈니스 로직 수행 중 발생하는 모든 예외의 <b>최상위 공통 클래스</b>입니다.
 * @implSpec 1. 시스템에서 정의한 모든 커스텀 예외는 이 클래스를 상속받아야 합니다.<br>
 * 2. 런타임 예외({@link RuntimeException})를 상속하여 서비스 레이어에서 별도의 {@code throws} 선언 없이 전역 핸들러까지 예외를 전달합니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 예외 발생 시 반환할 표준 에러 메타데이터
     */
    private final ErrorMessage errorMessage;

    /**
     * @param message 시스템 표준 에러 정의 객체
     * @responsibility 사전 정의된 {@link ErrorMessage} 규격에 따라 예외를 생성합니다.
     */
    public BusinessException(ErrorMessage message) {
        super(message.getMessage());
        this.errorMessage = message;
    }

    /**
     * @param message 시스템 표준 에러 정의 객체 {@link ErrorMessage}
     * @param reason  예외 발생에 대한 상세 설명
     * @responsibility 기본 에러 정의에 <b>상세 사유(Reason)</b>를 추가하여 예외를 생성합니다.
     * @implNote 필드 유효성 검사 실패나 구체적인 도메인 상황을 메시지에 동적으로 포함해야 할 때 사용합니다.
     */
    public BusinessException(ErrorMessage message, String reason) {
        super(reason);
        this.errorMessage = message;
    }

    /**
     * @param reason 예외 발생 원인 메시지
     * @responsibility 특정 에러 정의 없이 메시지만으로 예외를 발생시키며, 기본적으로 <b>500 Internal Server Error</b>로 취급합니다.
     */
    public BusinessException(String reason) {
        super(reason);
        this.errorMessage = ErrorMessage.INTERNAL_SERVER_ERROR;
    }
}