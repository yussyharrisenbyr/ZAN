package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.model.vo.HotBlogVO;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.ThumbService;
import com.example.dianzan.util.BlogImageUtils;
import com.example.dianzan.util.CacheSourceContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.example.dianzan.repository.BlogEsRepository;
import com.example.dianzan.model.entity.BlogDoc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;

@RestController
@RequestMapping("blog")
@Slf4j
public class BlogController {
    @Resource
    private BlogService blogService;
    @Resource
    private BlogEsRepository blogEsRepository;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private Cache<String, Object> localCache;
    @Resource
    private ThumbService thumbService;

    private static final String BLOG_LIST_CACHE_KEY = "blog:list";
    private static final String BLOG_LIST_CACHE_VERSION_KEY = "blog:list:version";
    private static final long BLOG_LIST_CACHE_TTL_SECONDS = 60;
    private static final long BLOG_LIST_VERSION_SYNC_INTERVAL_MS = 2000;
    private static final String BLOG_HOT_CACHE_KEY = "blog:hot";
    private static final String BLOG_HOT_CACHE_VERSION_KEY = "blog:hot:version";
    private static final long BLOG_HOT_CACHE_TTL_SECONDS = 60;
    private static final long BLOG_HOT_VERSION_SYNC_INTERVAL_MS = 2000;
    private final AtomicLong blogListCacheVersion = new AtomicLong(-1L);
    private final AtomicLong blogListVersionLastSyncTime = new AtomicLong(0L);
    private final AtomicLong blogHotCacheVersion = new AtomicLong(-1L);
    private final AtomicLong blogHotVersionLastSyncTime = new AtomicLong(0L);

    @GetMapping("/get")
    public BaseResponse<BlogVO> get(long blogId, HttpServletRequest request) {
        return ResultUtils.success(blogService.getBlogVOById(blogId, request));
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/list")
    public BaseResponse<List<BlogVO>> list(@RequestParam(required = false) Long cursor,
                                           @RequestParam(defaultValue = "10") int size,
                                           HttpServletRequest request) {
        List<Blog> blogs = loadBlogListWithCache(cursor, size, request);
        return ResultUtils.success(blogService.getBlogVOList(blogs, request));
    }

    public void warmupFirstPageCache(int size) {
        List<Blog> blogs = loadBlogListWithCache(null, size, null);
        log.info("首页首屏缓存预热完成，size={}, loaded={}", size, blogs == null ? 0 : blogs.size());
    }

    @GetMapping("/my")
    public BaseResponse<List<BlogVO>> my(HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        List<Blog> blogList = blogService.lambdaQuery().eq(Blog::getUserId, loginUser.getId()).list();
        return ResultUtils.success(blogService.getBlogVOList(blogList, request));
    }

    @GetMapping("/hot")
    public BaseResponse<List<HotBlogVO>> hot(@RequestParam(defaultValue = "5") int limit, HttpServletRequest request) {
        return ResultUtils.success(fillWeeklyHotThumbState(loadHotBlogsWithCache(limit, request), request));
    }

    public List<HotBlogVO> warmupHotListCache(int limit) {
        List<HotBlogVO> hotBlogs = loadHotBlogsWithCache(limit, null);
        log.info("热门列表缓存预热完成，limit={}, loaded={}", limit, hotBlogs == null ? 0 : hotBlogs.size());
        return hotBlogs;
    }

    @GetMapping("/user")
    public BaseResponse<BlogPageVO> listByUser(@RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "15") int size,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String keyword,
                                               HttpServletRequest request) {
        Long targetUserId = userId;
        if (targetUserId == null) {
            User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
            if (loginUser == null) {
                return ResultUtils.error(40100, "请先登录");
            }
            targetUserId = loginUser.getId();
        }
        return ResultUtils.success(blogService.listUserBlogs(targetUserId, cursor, size, sort, keyword, request));
    }

    @PostMapping("/publish")
    public BaseResponse<Long> publish(@RequestBody Blog blog, HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        if (blog.getTitle() == null || blog.getTitle().isBlank()) return ResultUtils.error(400, "标题不能为空");
        if (blog.getContent() == null || blog.getContent().isBlank()) return ResultUtils.error(400, "内容不能为空");
        applyBlogImagesForPersistence(blog, null);
         blog.setUserId(loginUser.getId());
        blog.setThumbCount(0);
        blogService.saveBlog(blog);
        invalidateBlogListCache();
        invalidateHotListCache();
        blogService.evictBlogDetailCache(blog.getId());
        // 同步到 ES
        BlogDoc doc = new BlogDoc();
        doc.setId(blog.getId());
        doc.setTitle(blog.getTitle());
        doc.setContent(blog.getContent());
        doc.setCoverImg(blog.getCoverImg());
        doc.setThumbCount(0);
        doc.setUserId(blog.getUserId());
         blogEsRepository.save(doc);
         return ResultUtils.success(blog.getId());
    }

    @PostMapping("/es/import")
    public BaseResponse<Integer> importToEs() {
        List<BlogDoc> docs = blogService.list().stream().map(b -> {
            BlogDoc doc = new BlogDoc();
            doc.setId(b.getId());
            doc.setTitle(b.getTitle());
            doc.setContent(b.getContent());
            doc.setCoverImg(b.getCoverImg());
            doc.setThumbCount(b.getThumbCount());
            doc.setUserId(b.getUserId());
            return doc;
        }).collect(Collectors.toList());
        blogEsRepository.saveAll(docs);
        return ResultUtils.success(docs.size());
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> update(@RequestBody Blog blog, HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        Blog existing = blogService.getById(blog.getId());
        if (existing == null) return ResultUtils.error(404, "文章不存在");
        if (!Objects.equals(existing.getUserId(), loginUser.getId()) && !Objects.equals(loginUser.getRole(), 1)) {
            return ResultUtils.error(403, "无权限编辑");
        }
        existing.setTitle(blog.getTitle());
        existing.setContent(blog.getContent());
        existing.setImageList(blog.getImageList() != null ? blog.getImageList() : BlogImageUtils.parseImageUrls(existing.getImageUrls()));
        existing.setCoverImg(blog.getCoverImg());
        applyBlogImagesForPersistence(existing, existing.getImageList());
        blogService.updateById(existing);
        invalidateBlogListCache();
        invalidateHotListCache();
        blogService.evictBlogDetailCache(existing.getId());
        // 同步 ES
        BlogDoc doc = new BlogDoc();
        doc.setId(existing.getId()); doc.setTitle(existing.getTitle());
        doc.setContent(existing.getContent()); doc.setCoverImg(existing.getCoverImg());
        doc.setThumbCount(existing.getThumbCount()); doc.setUserId(existing.getUserId());
        blogEsRepository.save(doc);
        return ResultUtils.success(true);
    }

    @DeleteMapping("/delete")
    public BaseResponse<Boolean> delete(long blogId, HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        Blog blog = blogService.getById(blogId);
        if (blog == null) return ResultUtils.error(404, "文章不存在");
        boolean isAdmin = Objects.equals(loginUser.getRole(), 1);
        if (!isAdmin && !Objects.equals(blog.getUserId(), loginUser.getId())) {
            return ResultUtils.error(403, "无权限删除");
        }
        blogService.removeById(blogId);
        invalidateBlogListCache();
        invalidateHotListCache();
        blogService.evictBlogDetailCache(blogId);
        blogEsRepository.deleteById(blogId);
        return ResultUtils.success(true);
    }

    private void applyBlogImagesForPersistence(Blog blog, List<String> fallbackImageList) {
        List<String> imageList = blog.getImageList() != null
                ? BlogImageUtils.resolveImageList(blog.getImageList(), blog.getImageUrls())
                : BlogImageUtils.normalizeImageList(fallbackImageList);
        blog.setImageList(imageList);
        blog.setImageUrls(BlogImageUtils.toImageUrlsJson(imageList));
        blog.setCoverImg(BlogImageUtils.resolveCoverImg(blog.getCoverImg(), imageList));
    }

    private List<HotBlogVO> fillWeeklyHotThumbState(List<HotBlogVO> hotBlogs, HttpServletRequest request) {
        if (hotBlogs == null || hotBlogs.isEmpty()) {
            return hotBlogs;
        }
        User loginUser = request == null ? null : (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        if (loginUser == null) {
            hotBlogs.forEach(item -> item.setHasThumb(false));
            return hotBlogs;
        }
        return hotBlogs.stream().map(item -> {
            HotBlogVO result = new HotBlogVO();
            result.setBlogId(item.getBlogId());
            result.setTitle(item.getTitle());
            result.setCoverImg(item.getCoverImg());
            result.setUserId(item.getUserId());
            result.setAuthorName(item.getAuthorName());
            result.setAuthorAvatarUrl(item.getAuthorAvatarUrl());
            result.setWeeklyThumbCount(item.getWeeklyThumbCount());
            result.setThumbCount(item.getThumbCount());
            result.setHasThumb(Boolean.TRUE.equals(thumbService.hasThumb(item.getBlogId(), loginUser.getId())));
            return result;
        }).toList();
    }

    private void invalidateBlogListCache() {
        bumpBlogListCacheVersion();
        // 清理本地短 TTL 列表缓存
        localCache.asMap().keySet().removeIf(k -> k.startsWith(BLOG_LIST_CACHE_KEY + ":"));
    }

    private void invalidateHotListCache() {
        bumpBlogHotCacheVersion();
        localCache.asMap().keySet().removeIf(k -> k.startsWith(BLOG_HOT_CACHE_KEY + ":"));
    }

    private long getBlogListCacheVersion() {
        long cachedVersion = blogListCacheVersion.get();
        long now = System.currentTimeMillis();
        if (cachedVersion > 0 && now - blogListVersionLastSyncTime.get() < BLOG_LIST_VERSION_SYNC_INTERVAL_MS) {
            return cachedVersion;
        }
        try {
            Object value = redisTemplate.opsForValue().get(BLOG_LIST_CACHE_VERSION_KEY);
            long version = parseCacheVersion(value);
            blogListCacheVersion.set(version);
            blogListVersionLastSyncTime.set(now);
            return blogListCacheVersion.get();
        } catch (Exception e) {
            log.warn("读取首页列表缓存版本失败，使用本地默认版本", e);
            blogListCacheVersion.compareAndSet(-1L, 1L);
            return blogListCacheVersion.get();
        }
    }

    private void bumpBlogListCacheVersion() {
        try {
            Long next = redisTemplate.opsForValue().increment(BLOG_LIST_CACHE_VERSION_KEY);
            blogListCacheVersion.set(next == null || next < 1 ? 1L : next);
            blogListVersionLastSyncTime.set(System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("更新首页列表缓存版本失败，退化为本地版本递增", e);
            blogListCacheVersion.updateAndGet(v -> v < 1 ? 1 : v + 1);
            blogListVersionLastSyncTime.set(System.currentTimeMillis());
        }
    }

    private long getBlogHotCacheVersion() {
        long cachedVersion = blogHotCacheVersion.get();
        long now = System.currentTimeMillis();
        if (cachedVersion > 0 && now - blogHotVersionLastSyncTime.get() < BLOG_HOT_VERSION_SYNC_INTERVAL_MS) {
            return cachedVersion;
        }
        try {
            Object value = redisTemplate.opsForValue().get(BLOG_HOT_CACHE_VERSION_KEY);
            long version = parseCacheVersion(value);
            blogHotCacheVersion.set(version);
            blogHotVersionLastSyncTime.set(now);
            return blogHotCacheVersion.get();
        } catch (Exception e) {
            log.warn("读取热门列表缓存版本失败，使用本地默认版本", e);
            blogHotCacheVersion.compareAndSet(-1L, 1L);
            return blogHotCacheVersion.get();
        }
    }

    private void bumpBlogHotCacheVersion() {
        try {
            Long next = redisTemplate.opsForValue().increment(BLOG_HOT_CACHE_VERSION_KEY);
            blogHotCacheVersion.set(next == null || next < 1 ? 1L : next);
            blogHotVersionLastSyncTime.set(System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("更新热门列表缓存版本失败，退化为本地版本递增", e);
            blogHotCacheVersion.updateAndGet(v -> v < 1 ? 1 : v + 1);
            blogHotVersionLastSyncTime.set(System.currentTimeMillis());
        }
    }

    private long parseCacheVersion(Object value) {
        if (value instanceof Number number) {
            return Math.max(1L, number.longValue());
        }
        if (value != null) {
            try {
                return Math.max(1L, Long.parseLong(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1L;
    }

    @SuppressWarnings("unchecked")
    private List<HotBlogVO> loadHotBlogsWithCache(int limit, HttpServletRequest request) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        long cacheVersion = getBlogHotCacheVersion();
        String cacheKey = BLOG_HOT_CACHE_KEY + ":v:" + cacheVersion + ":" + safeLimit;
        List<HotBlogVO> hotBlogs = (List<HotBlogVO>) localCache.getIfPresent(cacheKey);
        String cacheSource = "local";
        if (hotBlogs == null) {
            try {
                hotBlogs = (List<HotBlogVO>) redisTemplate.opsForValue().get(cacheKey);
                if (hotBlogs != null) {
                    cacheSource = "redis";
                }
            } catch (Exception e) {
                log.warn("读取热门列表缓存失败，降级查库: cacheKey={}", cacheKey, e);
            }
        }
        if (hotBlogs == null) {
            hotBlogs = blogService.listWeeklyHotBlogs(safeLimit);
            cacheSource = "db";
            try {
                redisTemplate.opsForValue().set(cacheKey, hotBlogs, BLOG_HOT_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("写入热门列表缓存失败，不影响主流程: cacheKey={}", cacheKey, e);
            }
        }
        localCache.put(cacheKey, hotBlogs);
        CacheSourceContext.markBlogHot(request, cacheSource, cacheVersion);
        return hotBlogs;
    }

    @SuppressWarnings("unchecked")
    private List<Blog> loadBlogListWithCache(Long cursor, int size, HttpServletRequest request) {
        long cacheVersion = getBlogListCacheVersion();
        String cacheKey = buildBlogListCacheKey(cursor, size, cacheVersion);
        List<Blog> blogs = (List<Blog>) localCache.getIfPresent(cacheKey);
        String cacheSource = "local";
        if (blogs == null) {
            try {
                blogs = (List<Blog>) redisTemplate.opsForValue().get(cacheKey);
                if (blogs != null) {
                    cacheSource = "redis";
                }
            } catch (Exception e) {
                log.warn("读取首页列表缓存失败，降级查库: cacheKey={}", cacheKey, e);
            }
        }
        if (blogs == null) {
            blogs = loadBlogsByCursor(cursor, size);
            cacheSource = "db";
            try {
                redisTemplate.opsForValue().set(cacheKey, blogs, BLOG_LIST_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("写入首页列表缓存失败，不影响主流程: cacheKey={}", cacheKey, e);
            }
        }
        localCache.put(cacheKey, blogs);
        CacheSourceContext.markBlogList(request, cacheSource, cacheVersion);
        return blogs;
    }

    private String buildBlogListCacheKey(Long cursor, int size) {
        return buildBlogListCacheKey(cursor, size, getBlogListCacheVersion());
    }

    private String buildBlogListCacheKey(Long cursor, int size, long version) {
        return BLOG_LIST_CACHE_KEY + ":v:" + version + ":" + (cursor == null ? "first" : cursor) + ":" + size;
    }

    private List<Blog> loadBlogsByCursor(Long cursor, int size) {
        return cursor == null
                ? blogService.lambdaQuery().orderByDesc(Blog::getId).last("limit " + size).list()
                : blogService.lambdaQuery().lt(Blog::getId, cursor).orderByDesc(Blog::getId).last("limit " + size).list();
    }
}
