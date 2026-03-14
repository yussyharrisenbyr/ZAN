package com.example.dianzan.interceptor;

import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.util.CacheSourceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 关键接口性能日志拦截器。
 */
@Slf4j
public class PerformanceLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "perfLog.startTime";
    private static final long WARN_THRESHOLD_MS = 500L;
    private static final int MAX_QUERY_LENGTH = 120;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        Object startAttr = request.getAttribute(START_TIME_ATTR);
        if (!(startAttr instanceof Long startTime)) {
            return;
        }
        long durationMs = System.currentTimeMillis() - startTime;
        HttpSession session = request.getSession(false);
        User loginUser = session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("method", request.getMethod());
        fields.put("uri", request.getRequestURI());
        fields.put("query", truncateQuery(request.getQueryString()));
        fields.put("status", response.getStatus());
        fields.put("durationMs", durationMs);
        fields.put("sessionExists", session != null);
        fields.put("loginUserPresent", loginUser != null);
        fields.put("anonymous", loginUser == null);
        fields.put("handler", resolveHandler(handler));
        appendRouteSpecificFields(request, fields);
        if (ex != null) {
            fields.put("exception", ex.getClass().getSimpleName());
        }

        String line = joinFields(fields);
        if (durationMs >= WARN_THRESHOLD_MS || ex != null || response.getStatus() >= 500) {
            log.warn("PERF {}", line);
        } else {
            log.info("PERF {}", line);
        }
    }

    private void appendRouteSpecificFields(HttpServletRequest request, Map<String, Object> fields) {
        String uri = request.getRequestURI();
        if ("/blog/list".equals(uri)) {
            String cursor = request.getParameter("cursor");
            String cacheSource = CacheSourceContext.readBlogList(request);
            fields.put("isFirstPage", cursor == null || cursor.isBlank());
            fields.put("size", safeParam(request.getParameter("size")));
            appendCacheFields(fields, cacheSource, CacheSourceContext.readBlogListVersion(request));
            return;
        }
        if ("/blog/hot".equals(uri)) {
            String cacheSource = CacheSourceContext.readBlogHot(request);
            fields.put("limit", safeParam(request.getParameter("limit")));
            appendCacheFields(fields, cacheSource, CacheSourceContext.readBlogHotVersion(request));
            return;
        }
        if ("/blog/get".equals(uri)) {
            String cacheSource = CacheSourceContext.readBlogDetail(request);
            fields.put("blogId", safeParam(request.getParameter("blogId")));
            appendCacheFields(fields, cacheSource, CacheSourceContext.readBlogDetailVersion(request));
            return;
        }
        if ("/notification/unread/count".equals(uri)) {
            fields.put("notifProbe", true);
        }
    }

    private String resolveHandler(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        }
        return handler == null ? "unknown" : handler.getClass().getSimpleName();
    }

    private void appendCacheFields(Map<String, Object> fields, String cacheSource, String cacheVersion) {
        fields.put("cacheSource", cacheSource);
        fields.put("cacheVersion", cacheVersion);
        fields.put("cacheLocalHit", CacheSourceContext.isLocal(cacheSource));
        fields.put("cacheRedisHit", CacheSourceContext.isRedis(cacheSource));
        fields.put("cacheDbFallback", CacheSourceContext.isDb(cacheSource));
    }

    private String truncateQuery(String query) {
        if (query == null || query.isBlank()) {
            return "-";
        }
        String compact = query.replaceAll("\\s+", " ");
        return compact.length() <= MAX_QUERY_LENGTH ? compact : compact.substring(0, MAX_QUERY_LENGTH) + "...";
    }

    private String safeParam(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private String joinFields(Map<String, Object> fields) {
        StringJoiner joiner = new StringJoiner(" ");
        fields.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }
}


