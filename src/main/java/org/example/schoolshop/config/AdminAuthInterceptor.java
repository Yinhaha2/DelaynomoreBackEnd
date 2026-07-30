package org.example.schoolshop.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final SchoolShopProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isAdminConfigured()) {
            throw BizException.forbidden("管理端未配置");
        }
        String token = request.getHeader("X-Admin-Token");
        if (token == null || !token.equals(properties.getAdmin().getToken())) {
            throw BizException.forbidden("管理端 Token 无效");
        }
        return true;
    }
}
