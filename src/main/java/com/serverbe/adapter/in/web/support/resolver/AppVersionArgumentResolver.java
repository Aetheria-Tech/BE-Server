package com.serverbe.adapter.in.web.support.resolver;

import com.serverbe.adapter.in.web.support.annotation.ExtractAppVersion;
import com.serverbe.infrastructure.util.DeviceUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @responsibility {@link ExtractAppVersion} 애노테이션이 붙은 컨트롤러에서 앱 버전을 가져오는데 사용합니다.
 * */
@Component
public class AppVersionArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ExtractAppVersion.class)
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
        return DeviceUtils.extractAppVersion(request); // DeviceUtils의 새 메서드 호출
    }
}