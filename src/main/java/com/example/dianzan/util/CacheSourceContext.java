package com.example.dianzan.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 记录单次请求的缓存命中来源。
 */
public final class CacheSourceContext {

    public static final String BLOG_LIST_CACHE_SOURCE_ATTR = "cacheSource.blogList";
    public static final String BLOG_LIST_CACHE_VERSION_ATTR = "cacheVersion.blogList";
    public static final String BLOG_HOT_CACHE_SOURCE_ATTR = "cacheSource.blogHot";
    public static final String BLOG_HOT_CACHE_VERSION_ATTR = "cacheVersion.blogHot";
    public static final String BLOG_DETAIL_CACHE_SOURCE_ATTR = "cacheSource.blogDetail";
    public static final String BLOG_DETAIL_CACHE_VERSION_ATTR = "cacheVersion.blogDetail";

    private CacheSourceContext() {
    }

    public static void markBlogList(HttpServletRequest request, String source) {
        mark(request, BLOG_LIST_CACHE_SOURCE_ATTR, source);
    }

    public static void markBlogList(HttpServletRequest request, String source, long version) {
        markBlogList(request, source);
        markVersion(request, BLOG_LIST_CACHE_VERSION_ATTR, version);
    }

    public static void markBlogHot(HttpServletRequest request, String source) {
        mark(request, BLOG_HOT_CACHE_SOURCE_ATTR, source);
    }

    public static void markBlogHot(HttpServletRequest request, String source, long version) {
        markBlogHot(request, source);
        markVersion(request, BLOG_HOT_CACHE_VERSION_ATTR, version);
    }

    public static void markBlogDetail(HttpServletRequest request, String source) {
        mark(request, BLOG_DETAIL_CACHE_SOURCE_ATTR, source);
    }

    public static void markBlogDetail(HttpServletRequest request, String source, long version) {
        markBlogDetail(request, source);
        markVersion(request, BLOG_DETAIL_CACHE_VERSION_ATTR, version);
    }

    public static String readBlogList(HttpServletRequest request) {
        return read(request, BLOG_LIST_CACHE_SOURCE_ATTR);
    }

    public static String readBlogListVersion(HttpServletRequest request) {
        return read(request, BLOG_LIST_CACHE_VERSION_ATTR);
    }

    public static String readBlogHot(HttpServletRequest request) {
        return read(request, BLOG_HOT_CACHE_SOURCE_ATTR);
    }

    public static String readBlogHotVersion(HttpServletRequest request) {
        return read(request, BLOG_HOT_CACHE_VERSION_ATTR);
    }

    public static String readBlogDetail(HttpServletRequest request) {
        return read(request, BLOG_DETAIL_CACHE_SOURCE_ATTR);
    }

    public static String readBlogDetailVersion(HttpServletRequest request) {
        return read(request, BLOG_DETAIL_CACHE_VERSION_ATTR);
    }

    public static boolean isLocal(String source) {
        return "local".equalsIgnoreCase(source);
    }

    public static boolean isRedis(String source) {
        return "redis".equalsIgnoreCase(source);
    }

    public static boolean isDb(String source) {
        return "db".equalsIgnoreCase(source);
    }

    private static void mark(HttpServletRequest request, String attr, String source) {
        if (request == null || source == null || source.isBlank()) {
            return;
        }
        request.setAttribute(attr, source);
    }

    private static void markVersion(HttpServletRequest request, String attr, long version) {
        if (request == null || version < 1) {
            return;
        }
        request.setAttribute(attr, version);
    }

    private static String read(HttpServletRequest request, String attr) {
        if (request == null) {
            return "-";
        }
        Object value = request.getAttribute(attr);
        return value == null ? "-" : String.valueOf(value);
    }
}

