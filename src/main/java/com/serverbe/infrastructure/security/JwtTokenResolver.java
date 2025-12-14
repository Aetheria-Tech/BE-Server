package com.serverbe.infrastructure.security;

import com.serverbe.application.port.in.security.TokenResolver;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JWT 토큰의 유효성을 검증하고 내부 정보를 추출하는 통합 컴포넌트입니다.
 */
@Slf4j
@Component
public class JwtTokenResolver implements TokenResolver {
    private final JwtParser parser;

    public JwtTokenResolver(JwtKeyManager jwtKeyManager) {
        // JwtKeyManager로부터 서명 키가 설정된 JwtParser를 주입받아 공유합니다.
        this.parser = jwtKeyManager.getParser();
    }

    /**
     * 토큰의 서명 및 구조적 유효성을 검증합니다.
     */
    @Override
    public boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) return false;
            parser.parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT Validation Failed] -> {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰에서 사용자 고유 식별자(ID)를 추출합니다.
     */
    @Override
    public Long getIdFromToken(String token) {
        String sub = getClaims(token).getSubject();
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
    @Override
    public List<String> getRolesFromToken(String token) {
        List<?> roles = getClaims(token).get("roles", List.class);

        return roles == null ?
                List.of() :
                roles.stream().map(String::valueOf).toList();
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
        } catch (JwtException e) {
            // 상세한 예외 처리가 필요하다면 여기서 ExpiredJwtException 등을 분기 처리할 수 있습니다.
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_INVALID, e.getMessage());
        }
    }
}