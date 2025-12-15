package com.serverbe.adapter.out.persistence;

import com.serverbe.adapter.out.persistence.converter.CryptoConverter;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.domain.model.vo.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"oauth_id", "provider"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oauth_id", nullable = false)
    private String oauthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(nullable = false)
    @Convert(converter = CryptoConverter.class)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 추가: OAuth 제공자로부터 받은 리프레시 토큰
    // 토큰이 매우 길 수 있으므로 TEXT 타입을 사용하거나 길이를 넉넉하게 설정합니다.
    @Column(name = "oauth_refresh_token", columnDefinition = "TEXT")
    @Convert(converter = CryptoConverter.class)
    private String oauthRefreshToken;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    private UserEntity(String oauthId, OAuthProvider provider, String email,
                       String nickname, Role role, String oauthRefreshToken) {
        this.oauthId = oauthId;
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.oauthRefreshToken = oauthRefreshToken;
    }

    // 비즈니스 로직: OAuth 리프레시 토큰 갱신
    public void updateOauthRefreshToken(String newToken) {
        this.oauthRefreshToken = newToken;
    }
}