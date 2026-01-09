package com.serverbe.infrastructure.security;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResult;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * @author Duskafka
 * @responsibility 유저의 인증 정보를 바탕으로 시스템 접근을 위한 <b>액세스 토큰(JWT)</b>과 세션 유지를 위한 <b>리프레시 토큰(Opaque Token)</b>을 생성합니다.
 * @implSpec {@link TokenProvider} 인터페이스의 구현체로, 보안성이 검증된 알고리즘을 사용하여 토큰의 무결성과 예측 불가능성을 보장합니다.
 * @see TokenProvider
 */
@Slf4j
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecureRandom secureRandom;
    private final SecretKey key;
    private final Duration accessTokenValidityInMinute;
    private final Duration refreshTokenValidateDay;
    private final int refreshTokenLength;
    private final String authorityKey;

    /**
     * @param secureRandom  암호학적으로 강력한 난수 생성기 {@link SecureRandom}
     * @param jwtProperties JWT 유효 기간 및 설정을 담은 프로퍼티 {@link JwtProperties}
     * @param jwtKeyManager 서명 키 공유를 위한 매니저 {@link JwtKeyManager}
     * @responsibility JWT 설정값과 키 매니저로부터 서명에 필요한 인프라 자원을 주입받아 초기화합니다.
     */
    public JwtTokenProvider(
            SecureRandom secureRandom,
            JwtProperties jwtProperties,
            JwtKeyManager jwtKeyManager
    ) {
        this.secureRandom = secureRandom;
        this.accessTokenValidityInMinute = jwtProperties.accessToken().validityInMinute();
        this.refreshTokenValidateDay = jwtProperties.refreshToken().expirationDays();
        this.refreshTokenLength = jwtProperties.refreshToken().byteLength();
        this.authorityKey = jwtProperties.authorityKey();
        this.key = jwtKeyManager.getKey();
    }

    /**
     * @param id   사용자의 고유 식별자
     * @param role 사용자의 권한 {@link Role}
     * @return 생성된 JWT 문자열과 만료 시간을 포함한 {@link AccessTokenResult}
     * @responsibility 유저 식별자와 권한 정보를 담은 <b>Stateless JWT</b>를 생성합니다.
     * @implNote 1. <b>Payload</b>: 유저 고유 ID({@code id})를 {@code subject}로, 권한 정보를 커스텀 클레임({@code authorityKey})으로 삽입합니다.<br>
     * 2. <b>Signature</b>: <b>HS512</b> 알고리즘을 사용하여 토큰의 위변조를 방지합니다.
     */
    @Override
    public AccessTokenResult generateAccessToken(Long id, Role role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMinute.toMillis());

        String compact = Jwts.builder()
                .setSubject(String.valueOf(id))
                .claim(authorityKey, role.name())
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
        return AccessTokenResult.of(compact, validity.toInstant().toEpochMilli());
    }

    /**
     * @param id   사용자의 고유 식별자
     * @param role 사용자의 권한 {@link Role}
     * @return 생성된 난수 토큰과 사용자 정보를 매핑한 {@link RefreshTokenResult}
     * @responsibility 보안 강화를 위해 정보를 담지 않는 <b>Opaque Token</b> 형태의 리프레시 토큰을 생성합니다.
     * @implNote 액세스 토큰과 달리 내부 페이로드가 없는 무작위 문자열을 생성하며, 이는 데이터베이스(Redis 등)와의 대조를 통해 유효성을 검증합니다.
     */
    @Override
    public RefreshTokenResult generateRefreshToken(Long id, Role role) {
        String opaqueToken = generateOpaqueToken();
        Instant expire = Instant.now().plus(refreshTokenValidateDay);

        return RefreshTokenResult.of(opaqueToken, String.valueOf(id), expire);
    }

    /**
     * @return 패딩이 제거된 안전한 무작위 문자열
     * @responsibility {@link SecureRandom}을 사용하여 추측 불가능한 <b>Base64 URL-safe</b> 문자열을 생성합니다.
     */
    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[refreshTokenLength];
        secureRandom.nextBytes(randomBytes);

        // Base64 URL-safe 인코더를 사용하여 문자열로 변환 (패딩 제거)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}