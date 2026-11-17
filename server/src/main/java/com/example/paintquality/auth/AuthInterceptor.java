package com.example.paintquality.auth;

import com.example.paintquality.common.ApiException;
import com.example.paintquality.user.InspectorUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = AuthInterceptor.class.getName() + ".CURRENT_USER";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw ApiException.unauthorized("请提供Bearer token");
        }
        InspectorUser user = authService.authenticate(authorization.substring(7).trim());
        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        return true;
    }
}
