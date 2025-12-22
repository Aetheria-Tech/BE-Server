package com.serverbe.infrastructure.config.converter;

import com.serverbe.domain.model.user.vo.OAuthProvider;
import org.springframework.core.convert.converter.Converter;

public class StringToOAuthProviderConverter implements Converter<String, OAuthProvider> {
    @Override
    public OAuthProvider convert(String source) {
        // 소문자로 들어와도 대문자로 바꿔서 Enum을 찾음
        return OAuthProvider.valueOf(source.toUpperCase());
    }
}