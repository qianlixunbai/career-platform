package com.careerplatform.config;

import com.careerplatform.auth.BearerTokenInterceptor;
import com.careerplatform.auth.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final BearerTokenInterceptor bearerTokenInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    public WebMvcConfig(
            BearerTokenInterceptor bearerTokenInterceptor,
            CurrentUserIdArgumentResolver currentUserIdArgumentResolver) {
        this.bearerTokenInterceptor = bearerTokenInterceptor;
        this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bearerTokenInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }
}
