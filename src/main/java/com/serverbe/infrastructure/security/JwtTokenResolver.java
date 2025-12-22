package com.serverbe.infrastructure.security;

import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * JWT 토큰의 유효성을 검증하고 내부 정보를 추출하는 통합 컴포넌트입니다.
 */
@Slf4j
@Component
public class JwtTokenResolver implements TokenResolver {
    private final JwtParser parser;
    private final String roles;
    private final int refreshTokenLength;

    public JwtTokenResolver(JwtKeyManager jwtKeyManager, JwtProperties jwtProperties) {
        // JwtKeyManager로부터 서명 키가 설정된 JwtParser를 주입받아 공유합니다.
        this.parser = jwtKeyManager.getParser();
        this.roles = jwtProperties.authorityKey();
        this.refreshTokenLength = jwtProperties.refreshToken().byteLength();
    }

    @Override
    public Authentication getAuthentication(String token) {
        Long userId = this.getIdFromToken(token);
        Role role = this.getRoleFromToken(token);

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(role.name())
        );

        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    /**
     * 토큰의 서명 및 구조적 유효성을 검증합니다.
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

    @Override
    public boolean validateRefreshToken(String refreshToken) {
            return StringUtils.hasText(refreshToken) && refreshToken.length() == refreshTokenLength;
    }

    /**
     * 토큰에서 사용자 고유 식별자(ID)를 추출합니다.
     */
    @Override
    public Long getIdFromToken(String token) {
        try {
            // 1. 일반적인 파싱 시도 (만료되지 않은 경우)
            return Long.valueOf(getClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            // 2. 만료된 경우 ExpiredJwtException 내부의 Claims에서 Subject 추출
            log.info("만료된 토큰에서 ID 추출 시도: {}", e.getClaims().getSubject());
            String sub = e.getClaims().getSubject();
            return parseId(sub);
        } catch (Exception e) {
            // 3. 서명 오류나 잘못된 형식 등은 예외 처리
            log.error("토큰 파싱 중 오류 발생: {}", e.getMessage());
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_INVALID, "유효하지 않은 토큰입니다.");
        }
    }

    private Long parseId(String sub) {
        try {
            return Long.valueOf(sub);
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    ErrorMessage.JWT_SUBJECT_IS_NOT_NUMBER,
                    "JWT 토큰의 Subject 형식이 올바르지 않습니다."
            );
        }
    }

    /**
     * 토큰에서 권한 목록(roles)을 추출합니다.
     */
    public Role getRoleFromToken(String token) {
        Claims claims = getClaims(token);

        // JwtProperties에 정의된 authorityKey(예: "auth")로 값을 가져옴
        String roleStr = claims.get(roles, String.class);

        // 문자열을 Role Enum으로 변환 (예: "USER" -> Role.USER)
        return Role.valueOf(roleStr);
    }

    /**
     * 내부적으로 토큰을 파싱하여 Claims를 반환합니다.
     * 파싱 과정에서 서명 검증 및 만료 체크가 자동으로 이루어집니다.
     */
    private Claims getClaims(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_EMPTY);
            }
            return parser.parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorMessage.JWT_TOKEN_EXPIRED, "토큰이 만료되었습니다.");
        } catch (JwtException e) {
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_INVALID, e.getMessage());
        }
    }

    /**
     * 토큰으로부터 만료 시간을 추출합니다.
     * * @param token JWT 토큰
     * @return 만료 시간 (Instant)
     */
    @Override
    public Instant getExpirationFromToken(String token) {
        try {
            // 새로 빌드하지 않고 주입받은 parser를 그대로 사용합니다.
            return parser.parseClaimsJws(token).getBody().getExpiration().toInstant();
        } catch (ExpiredJwtException e) {
            // 이미 만료된 경우에도 Redis 블랙리스트 TTL 설정을 위해 만료 시점을 추출합니다.
            return e.getClaims().getExpiration().toInstant();
        } catch (JwtException e) {
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_INVALID, e.getMessage());
        }
    }
}