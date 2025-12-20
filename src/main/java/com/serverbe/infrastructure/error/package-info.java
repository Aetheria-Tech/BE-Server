/**
 * <h2>Global Error Handling</h2>
 * <p>
 * 애플리케이션 전역에서 발생하는 예외를 포착하여 표준화된 에러 응답으로 변환합니다.
 * </p>
 *
 * <b>구성 요소:</b>
 * <ul>
 * <li><code>GlobalExceptionHandler</code>: @ControllerAdvice를 통한 중앙 집중식 예외 처리</li>
 * <li>ErrorCode: 에러 코드, 메시지, HTTP 상태 코드를 관리하는 Enum</li>
 * <li>ErrorResponse: 클라이언트에게 전달될 최종 에러 데이터 구조</li>
 * </ul>
 */
package com.serverbe.infrastructure.error;