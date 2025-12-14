/**
 * <h2>Outgoing Persistence Adapters</h2>
 * <p>
 * 영속성 기술(JPA, Querydsl 등)을 구현하여 데이터를 관리합니다.
 * </p>
 *
 * <b>더티 체킹 메커니즘:</b>
 * <ul>
 * <li>도메인 모델을 DB에 반영할 때, 먼저 기존 JPA 엔티티를 조회한 뒤 도메인 모델의 변경사항을
 * JPA 엔티티에 복사(Mapping)하여 더티 체킹을 유도하거나 명시적으로 <code>save()</code>를 호출합니다.</li>
 * <li>애플리케이션 레이어는 DB 기술이 JPA인지 무엇인지 알 필요가 없도록 격리됩니다.</li>
 * </ul>
 */
package com.serverbe.adapter.out.persistence;