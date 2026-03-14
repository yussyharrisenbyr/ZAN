package com.example.dianzan.interceptor;

import com.example.dianzan.constant.UserConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 续约拦截器：用户携带有效 Session 时，自动延长过期时间（默认续 30 分钟）
 */
public class RefreshInterceptor implements HandlerInterceptor {

    private static final int EXPIRE_SECONDS = 30 * 60;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(UserConstant.LOGIN_USER) != null) {
            session.setMaxInactiveInterval(EXPIRE_SECONDS);
        }
        return true;
    }
}
