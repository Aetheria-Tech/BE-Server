/**
 * <h2>Application Services</h2>
 * <p>
 * 도메인 로직을 조합하여 비즈니스 유스케이스를 구현합니다.
 * </p>
 *
 * <b>더티 체킹 및 트랜잭션:</b>
 * <ul>
 * <li>{@code @Transactional}을 통해 트랜잭션 경계를 설정합니다.</li>
 * <li><b>더티 체킹의 어려움:</b> 이 서비스는 JPA 엔티티가 아닌 '순수 도메인 모델'을 다룹니다.
 * 따라서 더티 체킹에 의존하기보다, 수정된 도메인 모델을 Persistence Port로 넘겨 명시적으로 저장하는 방식을 주로 사용합니다.</li>
 * </ul>
 */
package com.serverbe.application.service;