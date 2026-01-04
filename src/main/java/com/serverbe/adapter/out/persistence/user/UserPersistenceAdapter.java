package com.serverbe.adapter.out.persistence.user;

import com.serverbe.adapter.out.persistence.mapper.UserMapper;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.crypto.EncryptionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpaUserRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<User> findByOauthId(String oauthId, OAuthProvider provider) {
        return jpaUserRepository.findByOauthIdAndProvider(oauthId, provider)
                .map(this::mapToDomainWithMigration);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaUserRepository.findById(id)
                .map(this::mapToDomainWithMigration);
    }


    @Override
    public User save(User user) {
        // 도메인 모델을 엔티티로 변환 후 저장 (이때 CryptoConverter에 의해 암호화 실행됨)
        UserEntity entity = userMapper.toEntity(user);
        UserEntity savedEntity = jpaUserRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaUserRepository.existsByEmail(email);
    }

    @Override
    public void deleteById(Long id) {
        jpaUserRepository.deleteById(id);
    }

    /**
     * 엔티티를 도메인 객체로 변환하며, 암호화 버전이 낮은 경우 최신 버전으로 마이그레이션을 수행합니다.
     *
     * @implNote
     * {@link EncryptionContext}의 마이그레이션 플래그는 ThreadLocal 기반으로 동작하며,
     * {@code userMapper.toDomain(entity)} 호출 과정에서 연관 엔티티 매핑이나 중첩된 변환 로직에 의해
     * 플래그 상태가 변경되거나 초기화될 위험이 있습니다.
     * 따라서 매핑 수행 전 플래그 상태를 로컬 변수({@code needsMigration})에 스냅샷으로 저장하여
     * 매핑 로직의 부수 효과(Side-effect)에 관계없이 일관된 마이그레이션 판단을 보장합니다.
     *
     * @param entity 변환할 유저 엔티티
     * @return 변환된(또는 마이그레이션된) 유저 도메인 모델
     */
    private User mapToDomainWithMigration(UserEntity entity) {
        // 1. 매퍼 실행 전, 컨버터에 의해 세팅된 플래그를 로컬 변수에 즉시 저장(Snapshot)
        boolean needsMigration = EncryptionContext.isMigrationRequired();

        // 2. 도메인 변환 실행 (이 과정에서 ThreadLocal 상태가 변할 수 있음)
        User domainUser = userMapper.toDomain(entity);

        // 3. 미리 담아둔 스냅샷 변수로 마이그레이션 여부 판단
        if (needsMigration) {

            // 플래그 초기화 및 엔티티 강제 수정 상태 변경
            EncryptionContext.clear();
            entity.forceUpdate();

            // DB에 즉시 반영 (saveAndFlush를 통해 Converter 재실행 유도)
            jpaUserRepository.saveAndFlush(entity);

            // 최신화된 엔티티를 다시 도메인으로 변환하여 반환
            return userMapper.toDomain(entity);
        }

        return domainUser;
    }
}