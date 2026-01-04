package com.serverbe.adapter.out.persistence.user;

import com.serverbe.adapter.out.persistence.mapper.UserMapper;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.crypto.EncryptionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
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
     * 엔티티를 도메인으로 변환하면서, 구버전 데이터인 경우 자동으로 최신화(Save)를 수행합니다.
     */
    private User mapToDomainWithMigration(UserEntity entity) {
        // 1. 엔티티를 도메인으로 변환 (이때 AttributeConverter에서 플래그가 세팅될 수 있음)
        User domainUser = userMapper.toDomain(entity);

        // 2. 마이그레이션이 필요한지 확인
        if (EncryptionContext.isMigrationRequired()) {
            // 3. 플래그 즉시 초기화 (중요: 메모리 누수 방지)
            EncryptionContext.clear();

            // 4. 최신 키로 다시 저장 (AttributeConverter가 새 키로 암호화함)
            UserEntity savedEntity = jpaUserRepository.save(entity);

            // 5. 최신화된 엔티티를 다시 도메인으로 변환하여 반환
            return userMapper.toDomain(savedEntity);
        }

        return domainUser;
    }
}