package com.serverbe.adapter.out.persistence.user;

import com.serverbe.adapter.out.persistence.art.RunningArtEntity;
import com.serverbe.adapter.out.persistence.converter.CryptoConverter;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.domain.model.user.vo.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @Column(name = "oauth_provider", nullable = false)
    private OAuthProvider provider;

    @Column(name = "email", nullable = false)
    @Convert(converter = CryptoConverter.class)
    private String email;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "status_message")
    private String statusMessage;

    // OAuth 제공자로부터 받은 리프레시 토큰
    // 토큰이 매우 길 수 있으므로 TEXT 타입을 사용하거나 길이를 넉넉하게 설정합니다.
    @Column(name = "oauth_refresh_token", columnDefinition = "TEXT")
    @Convert(converter = CryptoConverter.class)
    private String oauthRefreshToken;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user")
    private List<RunningArtEntity> runningArtEntities = new ArrayList<>();

    public void addRunningArt(RunningArtEntity art) {
        this.runningArtEntities.add(art);
        if (art.getUser() != this) {
            art.assignUser(this);
        }
    }

    @Builder
    private UserEntity(
            Long id,
            String oauthId,
            OAuthProvider provider,
            String email,
            String nickname,
            Role role,
            String statusMessage,
            String oauthRefreshToken
    ) {
        this.id = id;
        this.oauthId = oauthId;
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.statusMessage = statusMessage;
        this.oauthRefreshToken = oauthRefreshToken;
    }
}