package com.serverbe.adapter.in.web.support.resolver;

import com.serverbe.adapter.in.web.support.annotation.ExtractAccessToken;
import com.serverbe.adapter.in.web.support.annotation.ExtractRefreshToken;
import com.serverbe.infrastructure.security.TokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class TokenArgumentResolver implements HandlerMethodArgumentResolver {

    private final TokenExtractor tokenExtractor;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 두 어노테이션 중 하나라도 붙어 있고, 타입이 String이면 지원
        return (parameter.hasParameterAnnotation(ExtractAccessToken.class) ||
                parameter.hasParameterAnnotation(ExtractRefreshToken.class))
                && String.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        // 1. Access Token 요청인 경우
        if (parameter.hasParameterAnnotation(ExtractAccessToken.class)) {
            return tokenExtractor.extractAccessToken(request);
        }

        // 2. Refresh Token 요청인 경우
        if (parameter.hasParameterAnnotation(ExtractRefreshToken.class)) {
            return tokenExtractor.extractRefreshToken(request);
        }

        return null;
    }
}