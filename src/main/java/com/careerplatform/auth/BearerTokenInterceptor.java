package com.careerplatform.auth;

import com.careerplatform.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class BearerTokenInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ID_ATTRIBUTE = "currentUserId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    public BearerTokenInterceptor(JwtTokenService jwtTokenService, UserService userService) {
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("缺少有效的 Bearer Token");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("缺少有效的 Bearer Token");
        }
        AuthenticatedUser user = jwtTokenService.parseToken(token);
        if (!userService.isActiveUser(user.userId())) {
            throw new UnauthorizedException("认证信息无效");
        }
        request.setAttribute(CURRENT_USER_ID_ATTRIBUTE, user.userId());
        return true;
    }
}
