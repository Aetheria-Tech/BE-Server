package com.serverbe.infrastructure.config.converter;

import com.serverbe.domain.model.user.vo.OAuthProvider;
import org.springframework.core.convert.converter.Converter;

/**
 * @responsibility HTTP 요청 파라미터로 전달된 문자열을 {@link OAuthProvider} 열거형으로 변환합니다.
 * @implSpec Spring의 {@link Converter} 인터페이스를 구현하여 웹 계층의 데이터 바인딩 과정에서 자동으로 동작합니다.
 */
public class StringToOAuthProviderConverter implements Converter<String, OAuthProvider> {

    /**
     * @param source 변환할 원본 문자열
     * @return 매핑된 {@link OAuthProvider} 상수
     * @throws IllegalArgumentException 일치하는 Enum 상수가 없을 경우 발생
     * @responsibility 입력된 문자열을 대문자로 변환하여 일치하는 {@link OAuthProvider} 상수를 반환합니다.
     * @implNote 소문자로 유입되는 경로 변수나 쿼리 파라미터(예: "kakao", "google")에 대해서도 유연하게 대응하기 위해 <b>toUpperCase()</b> 로직을 포함합니다.
     */
    @Override
    public OAuthProvider convert(String source) {
        // 소문자로 들어와도 대문자로 바꿔서 Enum을 찾음
        return OAuthProvider.valueOf(source.toUpperCase());
    }
}