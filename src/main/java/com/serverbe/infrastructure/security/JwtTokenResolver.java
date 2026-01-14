package com.serverbe.infrastructure.security;

import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.domain.exception.BusinessException;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * @author Duskafka
 * @responsibility 발급된 JWT 토큰 및 리프레시 토큰의 유효성을 검증하고, 토큰 페이로드에서 사용자 식별자 및 권한 정보를 추출하여 <b>SecurityContext</b>에 적합한 형태로 변환합니다.
 * @implSpec 1. <b>JwtParser</b>: {@link JwtKeyManager}에서 주입받은 공유 파서를 사용하여 서명을 검증합니다.<br>
 * 2. <b>예외 복구</b>: 토큰 재발급(Reissue) 로직을 지원하기 위해, 만료된 토큰({@link ExpiredJwtException})에서도 사용자 ID를 추출할 수 있는 특수 로직을 포함합니다.
 * @see TokenResolver
 */
@Slf4j
@Component
public class JwtTokenResolver implements TokenResolver {
    private final JwtParser parser;
    private final String roles;
    private final int refreshTokenLength;

    /**
     * @param jwtKeyManager 서명 키가 설정된 파서를 제공하는 매니저 {@link JwtKeyManager}
     * @param jwtProperties 토큰 사양(권한 키, 길이 등)을 담은 프로퍼티 {@link JwtProperties}
     * @responsibility 토큰 해석에 필요한 파서와 설정 정보를 주입받아 초기화합니다.
     */
    public JwtTokenResolver(JwtKeyManager jwtKeyManager, JwtProperties jwtProperties) {
        // JwtKeyManager로부터 서명 키가 설정된 JwtParser를 주입받아 공유합니다.
        this.parser = jwtKeyManager.getParser();
        this.roles = jwtProperties.authorityKey();
        this.refreshTokenLength = jwtProperties.refreshToken().byteLength();
    }

    /**
     * @param accessToken 검증된 액세스 토큰
     * @return {@link UsernamePasswordAuthenticationToken} 기반의 인증 객체
     * @responsibility 액세스 토큰에서 사용자 식별자와 권한을 추출하여 Spring Security의 인증 객체({@link Authentication})를 생성합니다.
     * @implNote 액세스 토큰은 페이로드에 정보를 포함하는 <b>JWT</b> 형식이므로 이 메서드를 통해 즉시 인증 객체화가 가능합니다.
     */
    @Override
    public Authentication getAuthentication(String accessToken) {
        Long userId = this.getIdFromToken(accessToken);
        Role role = this.getRoleFromToken(accessToken);

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(role.name())
        );

        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    /**
     * @param accessToken 검증할 JWT 문자열
     * @return 유효성 여부 (유효하지 않거나 만료된 경우 false)
     * @responsibility <b>액세스 토큰(JWT)</b>의 서명 위변조 여부 및 구조적 무결성을 검증합니다.
     */
    @Override
    public boolean validateAccessToken(String accessToken) {
        try {
            if (accessToken == null || accessToken.isBlank()) return false;
            parser.parseClaimsJws(accessToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT Validation Failed] -> {}", e.getMessage());
            return false;
        }
    }

    /**
     * @param refreshToken 검증할 리프레시 토큰
     * @return 유효성 여부
     * @responsibility <b>리프레시 토큰(Opaque)</b>의 형식적 유효성을 검증합니다.
     * @implNote 리프레시 토큰은 정보를 담지 않는 무작위 문자열이므로, 설정된 길이와 일치하는지를 우선적으로 확인합니다.
     */
    @Override
    public boolean validateRefreshToken(String refreshToken) {
        return StringUtils.hasText(refreshToken) && refreshToken.length() == refreshTokenLength;
    }

    /**
     * @param accessToken 식별자를 추출할 액세스 토큰
     * @return 사용자 고유 ID (Long)
     * @throws BusinessException 토큰 형식이 잘못되었거나 서명이 유효하지 않은 경우
     * @responsibility 토큰의 {@code sub} 클레임에서 사용자 고유 식별자(ID)를 추출합니다.
     * @implNote <b>재발급(Reissue) 전략</b>: 만료된 액세스 토큰을 들고 재발급을 요청하는 경우를 위해,
     * {@link ExpiredJwtException}이 발생하더라도 예외 객체 내의 Claims에서 ID를 안전하게 추출하여 반환합니다.
     */
    @Override
    public Long getIdFromToken(String accessToken) {
        try {
            // 1. 일반적인 파싱 시도 (만료되지 않은 경우)
            return Long.valueOf(getClaims(accessToken).getSubject());
        } catch (ExpiredJwtException e) {
            // 2. 만료된 경우 ExpiredJwtException 내부의 Claims에서 Subject 추출
            log.info("만료된 토큰에서 ID 추출 시도: {}", e.getClaims().getSubject());
            String sub = e.getClaims().getSubject();
            return parseId(sub);
        } catch (Exception e) {
            // 3. 서명 오류나 잘못된 형식 등은 예외 처리
            log.error("토큰 파싱 중 오류 발생: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_INVALID, "유효하지 않은 토큰입니다.");
        }
    }

    private Long parseId(String sub) {
        try {
            return Long.valueOf(sub);
        } catch (NumberFormatException e) {
            throw new AuthException(
                    AuthErrorCode.JWT_SUBJECT_IS_NOT_NUMBER,
                    "JWT 토큰의 Subject 형식이 올바르지 않습니다."
            );
        }
    }

    /**
     * @param accessToken 권한을 추출할 액세스 토큰
     * @return 사용자 역할(Role)
     * @responsibility 토큰의 페이로드에서 설정된 권한 키(예: roles)에 해당하는 값을 추출하여 {@link Role}로 변환합니다.
     */
    @Override
    public Role getRoleFromToken(String accessToken) {
        Claims claims = getClaims(accessToken);

        // JwtProperties에 정의된 authorityKey(예: "auth")로 값을 가져옴
        String roleStr = claims.get(roles, String.class);

        // 문자열을 Role Enum으로 변환 (예: "USER" -> Role.USER)
        return Role.valueOf(roleStr);
    }

    /**
     * @responsibility (Private) 공통 파싱 로직을 수행하며 만료 및 유효성 예외를 시스템 예외로 변환합니다.
     */
    private Claims getClaims(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_EMPTY);
            }
            return parser.parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            throw new AuthException(AuthErrorCode.JWT_TOKEN_EXPIRED, "토큰이 만료되었습니다.");
        } catch (JwtException e) {
            throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_INVALID, e.getMessage());
        }
    }

    /**
     * @param accessToken 액세스 토큰
     * @return 토큰의 만료 시각
     * @responsibility 토큰으로부터 만료 시점({@link Instant})을 추출합니다.
     * @implNote 토큰이 이미 만료된 경우에도 Redis 블랙리스트 등록 등을 위해 만료 시점을 정확히 반환합니다.
     */
    @Override
    public Instant getExpirationFromToken(String accessToken) {
        try {
            // 새로 빌드하지 않고 주입받은 parser를 그대로 사용합니다.
            return parser.parseClaimsJws(accessToken).getBody().getExpiration().toInstant();
        } catch (ExpiredJwtException e) {
            // 이미 만료된 경우에도 Redis 블랙리스트 TTL 설정을 위해 만료 시점을 추출합니다.
            return e.getClaims().getExpiration().toInstant();
        } catch (JwtException e) {
            throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_INVALID, e.getMessage());
        }
    }
}