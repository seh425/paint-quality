package com.example.paintquality.config;

import com.example.paintquality.auth.AuthInterceptor;
import com.example.paintquality.common.ApiException;
import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.user.InspectorUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && InspectorUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object user = request == null ? null : request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return user;
    }
}
