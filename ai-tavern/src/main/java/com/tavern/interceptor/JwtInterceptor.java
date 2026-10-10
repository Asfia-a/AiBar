package com.tavern.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.ErrorCode;
import com.tavern.common.JwtUtil;
import com.tavern.common.Result;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> PUBLIC_GET = List.of(
            "/api/health",
            "/api/characters",
            "/api/characters/hot",
            "/api/characters/new",
            "/api/characters/search",
            "/api/characters/{id}",
            "/api/characters/{id}/comments",
            "/api/tags",
            "/api/categories"
    );

    private static final List<String> PUBLIC_ALL = List.of(
            "/api/auth/**"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean publicPath = matches(PUBLIC_ALL, path)
                || ("GET".equalsIgnoreCase(method) && matches(PUBLIC_GET, path));

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                String token = auth.substring(7);
                UserContext.set(jwtUtil.getUserId(token), jwtUtil.getRole(token));
            } catch (Exception e) {
                if (!publicPath) {
                    writeUnauthorized(response);
                    return false;
                }
            }
        } else if (!publicPath) {
            writeUnauthorized(response);
            return false;
        }

        if (path.startsWith("/api/admin")) {
            Integer role = UserContext.getRole();
            if (role == null || role != 1) {
                response.setStatus(403);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
                return false;
            }
        }
        return true;
    }

    private boolean matches(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.UNAUTHORIZED)));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
