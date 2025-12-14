/**
 * <h2>Domain Exceptions</h2>
 * <p>
 * 비즈니스 규칙 위반 시 발생하는 도메인 전용 예외들입니다.
 * </p>
 *
 * <b>설계 원칙:</b>
 * <ul>
 * <li>HTTP 상태 코드나 특정 기술 스택에 종속되지 않는 이름을 사용합니다 (예: <code>InsufficientBalanceException</code>).</li>
 * <li>이 예외들은 글로벌 에러 핸들러에서 적절한 응답 코드로 매핑됩니다.</li>
 * </ul>
 */
package com.serverbe.domain.exception;