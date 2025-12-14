/**
 * <h2>Incoming Web Adapters</h2>
 * <p>
 * 외부의 HTTP 요청을 애플리케이션의 UseCase로 전달하는 입구입니다.
 * </p>
 *
 * <b>역할:</b>
 * <ul>
 * <li>HTTP 매핑, JSON 파싱 및 입력값 검증(Validation)</li>
 * <li>Web 전용 DTO를 Application Command 객체로 변환</li>
 * <li>응답 형식을 정의하며, 도메인 객체를 직접 노출하지 않습니다.</li>
 * </ul>
 */
package com.serverbe.adapter.in.web;