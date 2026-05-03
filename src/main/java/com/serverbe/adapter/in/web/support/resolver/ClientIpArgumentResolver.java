package com.serverbe.adapter.in.web.support.resolver;

import com.serverbe.adapter.in.web.support.annotation.ExtractIp;
import com.serverbe.infrastructure.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @responsibility {@link ExtractIp} 애노테이션이 붙은 컨트롤러에서 IP를 가져오도록 합니다.
 * */
@Component
public class ClientIpArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ExtractIp.class)
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
        return ClientIpUtils.getClientIp(request);
    }
}