package com.example.dianzan.config;

import com.example.dianzan.interceptor.LoginInterceptor;
import com.example.dianzan.interceptor.PerformanceLogInterceptor;
import com.example.dianzan.interceptor.RefreshInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] PUBLIC_PATH_PATTERNS = {
            "/",
            "/index.html",
            "/favicon.ico",
            "/user/login",
            "/user/login/**",
            "/user/logout",
            "/user/logout/**",
            "/user/get/login",
            "/user/get/login/**",
            "/user/profile",
            "/user/profile/**",
            "/user/register",
            "/user/register/**",
            "/blog/list",
            "/blog/list/**",
            "/blog/user",
            "/blog/user/**",
            "/blog/hot",
            "/blog/hot/**",
            "/blog/get",
            "/blog/get/**",
            "/favorite/user",
            "/favorite/user/**",
            "/thumb/user",
            "/thumb/user/**",
            "/follow/isFollow",
            "/follow/isFollow/**",
            "/follow/fans",
            "/follow/fans/**",
            "/follow/following",
            "/follow/following/**",
            "/site/overview",
            "/site/overview/**",
            "/blog/search",
            "/blog/search/**",
            "/comment/list",
            "/comment/list/**",
            "/notification/unread/count",
            "/notification/unread/count/**",
            "/feedback/submit",
            "/feedback/submit/**",
            "/health",
            "/health/**",
            "/error",
            "/error/**",
            "/**/*.html",
            "/**/*.js",
            "/**/*.css"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 续约拦截器：拦截所有请求，放行
        registry.addInterceptor(new RefreshInterceptor())
                .addPathPatterns("/**");

        registry.addInterceptor(new PerformanceLogInterceptor())
                .addPathPatterns(
                        "/blog/list",
                        "/blog/hot",
                        "/blog/get",
                        "/user/get/login",
                        "/notification/unread/count"
                );

        // 权限拦截器：只拦截需要登录的接口
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATH_PATTERNS);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
