package com.serverbe.infrastructure.security;

import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * JWT(JSON Web Token) 서명에 사용되는 **비밀 키({@code SecretKey})를 관리**하고,
 * JWT 유효성 검증 및 파싱을 위한 **{@code JwtParser} 인스턴스를 생성 및 제공**하는 컴포넌트입니다.
 *
 * <p>이 컴포넌트는 애플리케이션 시작 시점에 {@code jwt.secret} 프로퍼티를 로드하고
 * 보안 요구사항에 맞는지 검증하며, 유효한 키와 파서를 초기화합니다.</p>
 */
@Getter
@Component
public class JwtKeyManager {
    /** JWT 서명 및 유효성 검증에 사용되는 비밀 키입니다. */
    private final SecretKey key;
    /** JWT를 파싱하고 유효성을 검사하는 데 사용되는 파서입니다. */
    private final JwtParser parser;

    /**
     * {@code JwtKeyManager}의 생성자입니다.
     *
     * <p>주어진 {@code JWTProperties}로부터 비밀 문자열을 읽어와 Base64 디코딩하고,
     * 보안 요구사항(최소 길이)을 검증한 후 {@link SecretKey}와 {@link JwtParser}를 초기화합니다.</p>
     *
     * @param jwtProperties JWT 관련 설정 값들을 담고 있는 프로퍼티 객체입니다.
     * @throws BusinessException {@code jwt.secret}이 Base64 인코딩된 문자열이 아니거나
     * HS256에 적합한 최소 길이(256비트, 32바이트)를 만족하지 못할 경우 발생합니다.
     */
    public JwtKeyManager(JwtProperties jwtProperties) {
        final byte[] keyBytes;
        try {
            // Base64 인코딩된 비밀 키 문자열을 바이트 배열로 디코딩
            keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorMessage.INTERNAL_SERVER_ERROR,
                    "jwt.secret은 Base64 인코딩된 문자열이어야 합니다."
            );
        }

        // HS256 알고리즘의 최소 권장 길이인 256비트(32바이트) 검증
        if (keyBytes.length < 32) {
            throw new BusinessException(
                    ErrorMessage.INTERNAL_SERVER_ERROR,
                    "jwt.secret은 HS256에 적합한 최소 256비트(32바이트) 이상이어야 합니다."
            );
        }

        // HMAC SHA 키 생성
        this.key = Keys.hmacShaKeyFor(keyBytes);

        // JwtParser 초기화 및 설정 (서명 키 및 허용 오차 시간 설정)
        this.parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(jwtProperties.allowedClockSkewSeconds())
                .build();
    }
}