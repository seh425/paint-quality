package com.example.paintquality.config;

import com.example.paintquality.auth.AuthInterceptor;
import com.example.paintquality.common.ApiException;
import com.example.paintquality.common.RequireRoles;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRoles required = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
        }
        if (required == null) {
            return true;
        }
        InspectorUser user = (InspectorUser) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        for (Role role : required.value()) {
            if (user.getRole() == role) {
                return true;
            }
        }
        throw ApiException.forbidden("当前角色无权执行此操作");
    }
}
