package com.example.dianzan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianzan.constant.ThumbConstant;
import com.example.dianzan.manager.cache.CacheManager;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.mapper.FavoriteMapper;
import com.example.dianzan.mapper.UserMapper;
import com.example.dianzan.model.dto.thumb.ThumbRedisData;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.entity.Thumb;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.AuthorStatsVO;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.model.vo.HotBlogVO;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.ThumbService;
import com.example.dianzan.service.UserService;
import com.example.dianzan.util.BlogImageUtils;
import com.example.dianzan.util.CacheSourceContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author linfu
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2026-02-09 12:56:03
*/
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{
    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CacheManager cacheManager;
    @Resource
    private UserMapper userMapper;
    @Resource
    private Cache<String, Object> localCache;
    @Resource
    private BlogMapper blogMapper;
    @Resource
    private FavoriteMapper favoriteMapper;

    private static final String BLOG_DETAIL_CACHE_KEY_PREFIX = "blog:detail:";
    private static final String BLOG_DETAIL_CACHE_VERSION_KEY_PREFIX = "blog:detail:version:";
    private static final long BLOG_DETAIL_CACHE_TTL_SECONDS = 300;
    private static final int DEFAULT_USER_BLOG_PAGE_SIZE = 15;
    private static final int MAX_USER_BLOG_PAGE_SIZE = 30;
    private static final String USER_BLOG_SORT_THUMB = "thumb";
    private static final String USER_BLOG_SORT_LATEST = "latest";
    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return getOrLoadBlogDetailCache(blogId, loginUser == null ? null : loginUser.getId(), loginUser, request);
    }

    @Override
    public BlogVO warmupAnonymousBlogDetailCache(long blogId) {
        return getOrLoadBlogDetailCache(blogId, null, null, null);
    }

//    @Override
//    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
//        User loginUser = userService.getLoginUser(request);
//        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
//
//        if (ObjUtil.isNotEmpty(loginUser)) {
//            List<Object> blogIdList = blogList.stream().map(blog -> blog.getId().toString()).collect(Collectors.toList());
//            // 获取点赞
//            List<Object> thumbList = redisTemplate.opsForHash()
//                    .multiGet(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogIdList);
//            for (int i = 0; i < thumbList.size(); i++) {
//                if (thumbList.get(i) == null) {
//                    continue;
//                }
//                blogIdHasThumbMap.put(Long.valueOf(blogIdList.get(i).toString()), true);
//            }
//        }
//
//        return blogList.stream()
//                .map(blog -> {
//                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
//                    blogVO.setHasThumb(blogIdHasThumbMap.get(blog.getId()));
//                    return blogVO;
//                })
//                .toList();
//    }
    private static final String USER_BLOG_CURSOR_LATEST_PREFIX = "latest:";
    private static final String USER_BLOG_CURSOR_THUMB_PREFIX = "thumb:";

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        if (CollectionUtils.isEmpty(blogList)) {
            return Collections.emptyList();
        }
        User loginUser = userService.getLoginUser(request);

        // 批量查作者信息
        Set<Long> userIds = blogList.stream().map(Blog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 批量查点赞状态（从 Redis Hash 一次 multiGet，避免 N 次查询）
         Map<Long, Boolean> thumbMap = new HashMap<>();
        Map<Long, Boolean> favoriteMap = new HashMap<>();
        Set<Long> blogIds = blogList.stream().map(Blog::getId).collect(Collectors.toSet());
        Map<Long, Long> favoriteCountMap = loadFavoriteCountMap(blogIds);
        if (loginUser != null) {
             String redisKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
             List<Object> fields = blogList.stream().map(b -> (Object) b.getId().toString()).collect(Collectors.toList());
             List<Object> thumbValues = redisTemplate.opsForHash().multiGet(redisKey, fields);
             Set<Long> redisMissBlogIds = new HashSet<>();
            for (int i = 0; i < blogList.size(); i++) {
                Object val = thumbValues.get(i);
                if (val != null) {
                    Long thumbId;
                    if (val instanceof Number) {
                        thumbId = ((Number) val).longValue();
                    } else if (val instanceof ThumbRedisData) {
                        thumbId = ((ThumbRedisData) val).getThumbId();
                    } else {
                        thumbId = JSONUtil.toBean(val.toString(), ThumbRedisData.class).getThumbId();
                    }
                     boolean hasThumb = thumbId != null && thumbId > 0;
                     thumbMap.put(blogList.get(i).getId(), hasThumb);
                } else {
                    redisMissBlogIds.add(blogList.get(i).getId());
                }
            }

            // Redis 未命中时兜底查 DB，避免首页 hasThumb 与详情页不一致
             if (!redisMissBlogIds.isEmpty()) {
                List<Thumb> dbThumbs = thumbService.lambdaQuery()
                        .eq(Thumb::getUserId, loginUser.getId())
                        .in(Thumb::getBlogId, redisMissBlogIds)
                        .list();
                 dbThumbs.forEach(t -> thumbMap.put(t.getBlogId(), true));
            }

            if (!blogIds.isEmpty()) {
                favoriteMapper.selectExistingFavoriteBlogIds(loginUser.getId(), blogIds)
                        .forEach(blogId -> favoriteMap.put(blogId, true));
            }
        }

         Map<Long, AuthorStatsVO> authorStatsMap = loadAuthorStats(userIds);

        return blogList.stream().map(blog -> {
             BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
             applyBlogImages(blogVO, blog);
             User author = userMap.get(blog.getUserId());
             blogVO.setAuthorName(author == null ? "匿名作者" : author.getUsername());
             blogVO.setAuthorAvatarUrl(author == null ? null : author.getAvatarUrl());
             blogVO.setHasThumb(loginUser != null && Boolean.TRUE.equals(thumbMap.get(blog.getId())));
             blogVO.setHasFavorite(loginUser != null && Boolean.TRUE.equals(favoriteMap.get(blog.getId())));
             blogVO.setFavoriteCount(favoriteCountMap.getOrDefault(blog.getId(), 0L));
            applyAuthorStats(blogVO, authorStatsMap);
            return blogVO;
        }).collect(Collectors.toList());
    }

    @Override
    public BlogPageVO listUserBlogs(Long userId, String cursor, int size, String sort, String keyword, HttpServletRequest request) {
        int normalizedSize = normalizePageSize(size);
        String normalizedSort = normalizeSort(sort);
        String normalizedKeyword = normalizeKeyword(keyword);
        UserBlogCursor cursorInfo = parseUserBlogCursor(cursor, normalizedSort);

        BlogPageVO result = new BlogPageVO();
        result.setSize(normalizedSize);
        result.setSort(normalizedSort);
        result.setKeyword(normalizedKeyword);
        result.setHasMore(false);
        result.setNextCursor("");

        if (userId == null) {
            return result;
        }

        Long total = this.baseMapper.selectCount(buildUserBlogQuery(userId, normalizedKeyword));
        long safeTotal = total == null ? 0L : total;
        result.setTotal(safeTotal);
        if (safeTotal <= 0) {
            return result;
        }

        LambdaQueryWrapper<Blog> listWrapper = buildUserBlogQuery(userId, normalizedKeyword);
        applyUserBlogCursor(listWrapper, normalizedSort, cursorInfo);
        applyUserBlogSort(listWrapper, normalizedSort);
        listWrapper.last("limit " + (normalizedSize + 1));

        List<Blog> blogList = this.list(listWrapper);
        boolean hasMore = blogList.size() > normalizedSize;
        List<Blog> currentBatch = hasMore ? blogList.subList(0, normalizedSize) : blogList;
        List<BlogVO> voList = getBlogVOList(currentBatch, request);
        result.setList(voList);
        result.setHasMore(hasMore);
        if (hasMore && !currentBatch.isEmpty()) {
            result.setNextCursor(buildUserBlogCursor(currentBatch.get(currentBatch.size() - 1), normalizedSort));
        }
        return result;
    }

    // 假设这是你新增博客的方法
    public boolean saveBlog(Blog blog) {
        boolean success = this.save(blog);
        if (success) {
            // 【关键】将新发布的博客ID加入布隆过滤器，防止它被误判为恶意请求！
            cacheManager.putBlog(blog.getId());
        }
        return success;
    }

    public void evictBlogDetailCache(Long blogId) {
        if (blogId == null) return;
        String prefix = BLOG_DETAIL_CACHE_KEY_PREFIX + blogId + ":";
        localCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        try {
            redisTemplate.opsForValue().increment(BLOG_DETAIL_CACHE_VERSION_KEY_PREFIX + blogId);
        } catch (Exception e) {
            log.warn("更新博客详情缓存版本失败，blogId={}", blogId, e);
        }
    }

    private String buildBlogDetailCacheKey(long blogId, Long userId) {
        return buildBlogDetailCacheKey(blogId, getBlogDetailCacheVersion(blogId), userId);
    }

    private String buildBlogDetailCacheKey(long blogId, long version, Long userId) {
        return BLOG_DETAIL_CACHE_KEY_PREFIX + blogId + ":v:" + version + ":u:" + (userId == null ? 0 : userId);
    }

    private BlogVO getOrLoadBlogDetailCache(long blogId, Long viewerId, User loginUser, HttpServletRequest request) {
        long cacheVersion = getBlogDetailCacheVersion(blogId);
        String cacheKey = buildBlogDetailCacheKey(blogId, cacheVersion, viewerId);
        BlogVO cached = (BlogVO) localCache.getIfPresent(cacheKey);
        String cacheSource = "local";
        if (cached == null) {
            try {
                Object redisVal = redisTemplate.opsForValue().get(cacheKey);
                if (redisVal instanceof BlogVO) {
                    cached = (BlogVO) redisVal;
                    cacheSource = "redis";
                }
            } catch (Exception ignored) {
            }
            if (cached != null) {
                localCache.put(cacheKey, cached);
            }
        }

        if (cached != null) {
            CacheSourceContext.markBlogDetail(request, cacheSource, cacheVersion);
            return cached;
        }

        Blog blog = this.getById(blogId);
        BlogVO result = this.getBlogVO(blog, loginUser);
        if (result == null) {
            return null;
        }
        cacheSource = "db";
        redisTemplate.opsForValue().set(cacheKey, result, BLOG_DETAIL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        localCache.put(cacheKey, result);
        CacheSourceContext.markBlogDetail(request, cacheSource, cacheVersion);
        return result;
    }

    private long getBlogDetailCacheVersion(long blogId) {
        try {
            Object value = redisTemplate.opsForValue().get(BLOG_DETAIL_CACHE_VERSION_KEY_PREFIX + blogId);
            return parseCacheVersion(value);
        } catch (Exception e) {
            log.warn("读取博客详情缓存版本失败，blogId={}", blogId, e);
            return 1L;
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
    private BlogVO getBlogVO(Blog blog, User loginUser) {
        if (blog == null) {
            return null;
        }
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);
        applyBlogImages(blogVO, blog);
        User author = userMapper.selectById(blog.getUserId());
        blogVO.setAuthorName(author != null ? author.getUsername() : "匿名作者");
        blogVO.setAuthorAvatarUrl(author == null ? null : author.getAvatarUrl());
        blogVO.setFavoriteCount(loadFavoriteCountMap(Collections.singleton(blog.getId())).getOrDefault(blog.getId(), 0L));
        if (loginUser != null) {
            Boolean exist = thumbService.hasThumb(blog.getId(), loginUser.getId());
            blogVO.setHasThumb(Boolean.TRUE.equals(exist));
            blogVO.setHasFavorite(!favoriteMapper.selectExistingFavoriteBlogIds(loginUser.getId(), Collections.singleton(blog.getId())).isEmpty());
        } else {
            blogVO.setHasThumb(false);
            blogVO.setHasFavorite(false);
        }
        applyAuthorStats(blogVO, loadAuthorStats(Collections.singleton(blog.getUserId())));
        return blogVO;
    }

    private void applyBlogImages(BlogVO blogVO, Blog blog) {
        List<String> imageList = BlogImageUtils.resolveImageList(blog.getImageList(), blog.getImageUrls());
        blogVO.setImageList(imageList);
        blogVO.setCoverImg(BlogImageUtils.resolveCoverImg(blog.getCoverImg(), imageList));
    }

    private Map<Long, Long> loadFavoriteCountMap(Collection<Long> blogIds) {
        if (CollectionUtils.isEmpty(blogIds)) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = favoriteMapper.selectFavoriteCountByBlogIds(blogIds);
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyMap();
        }
        Map<Long, Long> result = new HashMap<>();
        rows.forEach(row -> {
            Object blogIdValue = row.get("blogId");
            Object countValue = row.get("favoriteCount");
            if (blogIdValue == null || countValue == null) {
                return;
            }
            Long blogId = blogIdValue instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(blogIdValue));
            Long count = countValue instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(countValue));
            result.put(blogId, count);
        });
        return result;
    }

    private LambdaQueryWrapper<Blog> buildUserBlogQuery(Long userId, String keyword) {
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .eq(Blog::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(q -> q.like(Blog::getTitle, keyword)
                    .or()
                    .like(Blog::getContent, keyword));
        }
        return wrapper;
    }

    private void applyUserBlogCursor(LambdaQueryWrapper<Blog> wrapper, String sort, UserBlogCursor cursor) {
        if (cursor == null || cursor.getLastId() == null) {
            return;
        }
        if (USER_BLOG_SORT_THUMB.equals(sort) && cursor.getLastThumbCount() != null) {
            wrapper.and(q -> q.lt(Blog::getThumbCount, cursor.getLastThumbCount())
                    .or(inner -> inner.eq(Blog::getThumbCount, cursor.getLastThumbCount())
                            .lt(Blog::getId, cursor.getLastId())));
            return;
        }
        wrapper.lt(Blog::getId, cursor.getLastId());
    }

    private void applyUserBlogSort(LambdaQueryWrapper<Blog> wrapper, String sort) {
        if (USER_BLOG_SORT_THUMB.equals(sort)) {
            wrapper.orderByDesc(Blog::getThumbCount)
                    .orderByDesc(Blog::getId);
            return;
        }
        wrapper.orderByDesc(Blog::getId);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_USER_BLOG_PAGE_SIZE;
        }
        return Math.min(size, MAX_USER_BLOG_PAGE_SIZE);
    }

    private String normalizeSort(String sort) {
        return USER_BLOG_SORT_THUMB.equalsIgnoreCase(String.valueOf(sort))
                ? USER_BLOG_SORT_THUMB
                : USER_BLOG_SORT_LATEST;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private UserBlogCursor parseUserBlogCursor(String cursor, String sort) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            if (USER_BLOG_SORT_THUMB.equals(sort) && cursor.startsWith(USER_BLOG_CURSOR_THUMB_PREFIX)) {
                String payload = cursor.substring(USER_BLOG_CURSOR_THUMB_PREFIX.length());
                String[] parts = payload.split(":", 2);
                if (parts.length != 2) {
                    return null;
                }
                Integer thumbCount = Integer.valueOf(parts[0]);
                Long lastId = Long.valueOf(parts[1]);
                return new UserBlogCursor(lastId, thumbCount);
            }
            if (cursor.startsWith(USER_BLOG_CURSOR_LATEST_PREFIX)) {
                Long lastId = Long.valueOf(cursor.substring(USER_BLOG_CURSOR_LATEST_PREFIX.length()));
                return new UserBlogCursor(lastId, null);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private String buildUserBlogCursor(Blog blog, String sort) {
        if (blog == null || blog.getId() == null) {
            return "";
        }
        if (USER_BLOG_SORT_THUMB.equals(sort)) {
            int thumbCount = blog.getThumbCount() == null ? 0 : blog.getThumbCount();
            return USER_BLOG_CURSOR_THUMB_PREFIX + thumbCount + ":" + blog.getId();
        }
        return USER_BLOG_CURSOR_LATEST_PREFIX + blog.getId();
    }

    private static final class UserBlogCursor {
        private final Long lastId;
        private final Integer lastThumbCount;

        private UserBlogCursor(Long lastId, Integer lastThumbCount) {
            this.lastId = lastId;
            this.lastThumbCount = lastThumbCount;
        }

        private Long getLastId() {
            return lastId;
        }

        private Integer getLastThumbCount() {
            return lastThumbCount;
        }
    }

    private Map<Long, AuthorStatsVO> loadAuthorStats(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<AuthorStatsVO> stats = blogMapper.selectAuthorStats(userIds);
        if (CollectionUtils.isEmpty(stats)) {
            return Collections.emptyMap();
        }
        return stats.stream().collect(Collectors.toMap(AuthorStatsVO::getUserId, vo -> vo));
    }

    private void applyAuthorStats(BlogVO blogVO, Map<Long, AuthorStatsVO> statsMap) {
        if (blogVO == null || blogVO.getUserId() == null) {
            return;
        }
        AuthorStatsVO stat = statsMap.get(blogVO.getUserId());
        if (stat == null) {
            blogVO.setAuthorArticleCount(0L);
            blogVO.setAuthorLikeCount(0L);
            blogVO.setAuthorFavoriteCount(0L);
            return;
        }
        blogVO.setAuthorArticleCount(stat.getArticleCount());
        blogVO.setAuthorLikeCount(stat.getLikeCount());
        blogVO.setAuthorFavoriteCount(stat.getFavoriteCount());
    }

    @Override
    public List<HotBlogVO> listWeeklyHotBlogs(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<HotBlogVO> hotList = blogMapper.selectWeeklyHotBlogs(
                java.util.Date.from(since.atZone(ZoneId.systemDefault()).toInstant()), safeLimit);
        if (!CollectionUtils.isEmpty(hotList)) {
            return hotList;
        }
        List<Blog> fallback = this.lambdaQuery()
                .orderByDesc(Blog::getThumbCount)
                .last("limit " + safeLimit)
                .list();
        if (CollectionUtils.isEmpty(fallback)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = fallback.stream().map(Blog::getUserId).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return fallback.stream().map(blog -> {
            HotBlogVO vo = new HotBlogVO();
            vo.setBlogId(blog.getId());
            vo.setTitle(blog.getTitle());
            vo.setCoverImg(blog.getCoverImg());
            vo.setUserId(blog.getUserId());
            vo.setAuthorName(userNameMap.getOrDefault(blog.getUserId(), "匿名作者"));
            vo.setThumbCount(blog.getThumbCount());
            long fallbackCount = blog.getThumbCount() == null ? 0L : blog.getThumbCount().longValue();
            vo.setWeeklyThumbCount(fallbackCount);
            return vo;
        }).collect(Collectors.toList());
    }
}




