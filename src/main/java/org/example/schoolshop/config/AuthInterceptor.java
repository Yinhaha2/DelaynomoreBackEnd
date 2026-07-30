package org.example.schoolshop.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> WHITE_LIST = Set.of(
            "POST:/api/auth/wx-login",
            "GET:/api/posts",
            "GET:/api/tasks",
            "GET:/api/materials",
            "GET:/api/health",
            "GET:/api/rankings",
            "GET:/api/campus/spots",
            "GET:/api/campus/map/tasks",
            "GET:/api/campus/events",
            "GET:/api/partners",
            "GET:/api/courses",
            "GET:/api/search/hot"
    );

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = request.getMethod() + ":" + normalizePath(request);
        boolean optionalAuth = WHITE_LIST.contains(key) || isPublicDetail(request);

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Long userId = jwtUtil.parseUserId(auth.substring(7).trim());
                UserContext.setUserId(userId);
            } catch (Exception e) {
                if (!optionalAuth) {
                    throw BizException.unauthorized("Token 无效");
                }
            }
        } else if (!optionalAuth) {
            throw BizException.unauthorized("请先登录");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean isPublicDetail(HttpServletRequest request) {
        String path = normalizePath(request);
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        return path.matches("/api/posts/\\d+")
                || path.matches("/api/tasks/\\d+")
                || path.matches("/api/materials/\\d+")
                || path.matches("/api/campus/spots/\\d+")
                || path.matches("/api/courses/\\d+");
    }

    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.replaceAll("/\\d+", "/{id}");
    }
}
