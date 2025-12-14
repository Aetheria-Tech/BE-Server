/**
 * <h2>Pure Domain Models</h2>
 * <p>
 * 외부 기술(JPA, Web 등)에 의존하지 않는 순수한 비즈니스 객체입니다.
 * </p>
 *
 * <b>특징:</b>
 * <ul>
 * <li>JPA 어노테이션({@code @Entity}, {@code @Column})을 사용하지 않습니다.</li>
 * <li>도메인의 핵심 상태와 행위를 포함하며, 가장 테스트하기 쉬운 코드여야 합니다.</li>
 * <li>Persistence 레이어로 넘어갈 때 JPA 엔티티로 변환(Mapping)됩니다.</li>
 * </ul>
 */
package com.serverbe.domain.model;