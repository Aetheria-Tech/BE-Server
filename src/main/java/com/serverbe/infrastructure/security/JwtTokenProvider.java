package com.serverbe.infrastructure.security;

import com.serverbe.application.port.in.security.TokenProvider;
import com.serverbe.application.port.in.dto.RefreshTokenIssueResult;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * 이 클래스는 Spring Security의 {@link Authentication} 정보를 기반으로
 * 액세스 토큰(Access Token)과 리프레시 토큰(Refresh Token)을 생성하는 역할을 수행합니다.
 */
@Slf4j
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecureRandom secureRandom;

    private final SecretKey KEY;
    private final Duration ACCESS_TOKEN_VALIDITY_IN_HOUR;
    private final long REFRESH_TOKEN_VALIDATE_DAY;
    private final int REFRESH_TOKEN_LENGTH;

    /**
     * {@code JwtTokenProvider}의 생성자입니다.
     *
     * @param jwtProperties JWT 관련 설정 값들을 담고 있는 프로퍼티 객체입니다.
     * @param jwtKeyManager JWT 서명 키를 관리하는 컴포넌트입니다.
     */
    public JwtTokenProvider(
            SecureRandom secureRandom,
            JwtProperties jwtProperties,
            JwtKeyManager jwtKeyManager
    ) {
        this.secureRandom = secureRandom;

        this.ACCESS_TOKEN_VALIDITY_IN_HOUR = jwtProperties.accessToken().validityInMinute();
        this.REFRESH_TOKEN_VALIDATE_DAY = jwtProperties.refreshToken().expirationDays();
        this.REFRESH_TOKEN_LENGTH = jwtProperties.refreshToken().byteLength();
        // 서명 키를 KeyManager로부터 가져옵니다.
        KEY = jwtKeyManager.getKey();
    }

    /**
     * 주어진 {@link Authentication} 객체를 사용하여 **액세스 토큰**을 생성합니다.
     *
     * <p>액세스 토큰에는 다음 정보가 포함됩니다:</p>
     * <ul>
     * <li>Subject (sub): 사용자 ID ({@code authentication.getName()})</li>
     * <li>Issued At (iat): 토큰 발급 시간</li>
     * <li>Expiration (exp): 토큰 만료 시간 ({@code ACCESS_TOKEN_VALIDITY_IN_HOUR} 기준)</li>
     * <li>Custom Claim: "roles" (사용자의 권한 목록)</li>
     * </ul>
     *
     * @param authentication 토큰 생성에 사용될 사용자 인증 정보입니다.
     * @return 생성된 액세스 토큰 문자열입니다.
     */
    @Override
    public String generateAccessToken(Authentication authentication) {
        // 사용자 이름(Principal, 여기서는 runner ID)을 토큰의 subject로 설정
        String subject = authentication.getName();

        // 사용자의 권한(역할) 목록 추출
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Instant now = Instant.now();
        Instant expiration = now.plus(ACCESS_TOKEN_VALIDITY_IN_HOUR);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .claim("roles", roles) // Custom Claim으로 역할 목록 추가
                .setExpiration(Date.from(expiration))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 주어진 {@link Authentication} 객체를 사용하여 **리프레시 토큰**을 생성합니다.
     *
     * <p>리프레시 토큰에는 다음 정보가 포함됩니다:</p>
     * <ul>
     * <li>Subject (sub): 사용자 ID ({@code authentication.getName()})</li>
     * <li>Issued At (iat): 토큰 발급 시간</li>
     * <li>Expiration (exp): 토큰 만료 시간 ({@code REFRESH_TOKEN_VALIDATE_DAY} 기준)</li>
     * <li>Custom Claim: "jti" (JWT ID, 토큰의 고유 식별자)</li>
     * </ul>
     *
     * @param authentication 토큰 생성에 사용될 사용자 인증 정보입니다.
     * @return 생성된 리프레시 토큰 문자열과 관련 정보를 담은 {@code RefreshTokenIssueResult}입니다.
     */
    @Override
    public RefreshTokenIssueResult generateRefreshToken(Authentication authentication) {
        log.debug("[TokenProvider] createRefreshToken({})", authentication.getName());

        String opaqueToken = generateOpaqueToken();
        Instant expire = Instant.now().plus(Duration.ofDays(REFRESH_TOKEN_VALIDATE_DAY));

        log.info("[TokenProvider] Refresh Token created for username: {}. Token length: {}", authentication.getName(), opaqueToken.length());

        return RefreshTokenIssueResult.of(opaqueToken, authentication.getName(), expire);
    }

    /**
     * 리프레시 토큰에 필요한 클레임({@code Claims}) 객체를 생성합니다.
     *
     * @param username 토큰의 subject에 들어갈 사용자 이름(ID)입니다.
     * @param jti 토큰의 고유 ID(JWT ID)입니다.
     * @return 생성된 {@code Claims} 객체입니다.
     */
    private Claims createRefreshTokenClaims(String username, String jti) {
        Claims claims = Jwts.claims().setSubject(String.valueOf(username));
        claims.put("jti", jti); // JWT ID 클레임 추가
        return claims;
    }

    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);

        // Base64 URL-safe 인코더를 사용하여 문자열로 변환 (패딩 제거)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}