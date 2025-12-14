/**
 * <h2>Domain Services</h2>
 * <p>
 * 단일 엔티티(Entity)에 담기 어려운 비즈니스 로직을 처리하는 도메인 서비스입니다.
 * </p>
 *
 * <b>사용 지침:</b>
 * <ul>
 * <li>여러 엔티티를 조합하여 복잡한 비즈니스 규칙을 계산하거나 상태를 변경할 때 사용합니다.</li>
 * <li>애플리케이션 서비스(Application Service)가 이 서비스를 호출하여 비즈니스 유스케이스를 완성합니다.</li>
 * <li><b>주의:</b> 외부에 의존(DB 접근 등)하지 않으며 오직 순수 도메인 모델 간의 협력만 다룹니다.</li>
 * </ul>
 */
package com.serverbe.domain.service;