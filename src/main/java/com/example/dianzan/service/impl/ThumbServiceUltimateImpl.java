package com.example.dianzan.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianzan.constant.ThumbConstant;
import com.example.dianzan.exception.BusinessException;
import com.example.dianzan.exception.ErrorCode;
import com.example.dianzan.listener.thumb.msg.ThumbEvent;
import com.example.dianzan.manager.cache.CacheManager;
import com.example.dianzan.mapper.NotificationMapper;
import com.example.dianzan.mapper.ThumbMapper;
import com.example.dianzan.model.dto.thumb.DoThumbRequest;
import com.example.dianzan.model.dto.thumb.ThumbRedisData;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.entity.Notification;
import com.example.dianzan.model.entity.Thumb;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.model.vo.ThumbActionResponse;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.ThumbService;
import com.example.dianzan.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceUltimateImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    private final UserService userService;
    private final BlogService blogService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PulsarTemplate<ThumbEvent> pulsarTemplate;
    private final CacheManager cacheManager;
    private final NotificationMapper notificationMapper;
    private final Cache<String, Object> localCache;
    private static final String BLOG_LIST_VERSION_KEY = "blog:list:version";
    private static final String BLOG_HOT_VERSION_KEY = "blog:hot:version";
    private static final int DEFAULT_THUMB_PAGE_SIZE = 15;
    private static final int MAX_THUMB_PAGE_SIZE = 30;
    private static final String THUMB_CURSOR_PREFIX = "liked:";

    private static final DefaultRedisScript<Long> THUMB_LUA_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('HGET', KEYS[1], ARGV[1]) " +
            "if v ~= false then " +
            "  local data = cjson.decode(v) " +
            "  if data['thumbId'] == nil or data['thumbId'] ~= -1 then return -1 end " +
            "end " +
            "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) return 1",
            Long.class
    );

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        // 【绝对防御防线】：第一步，先问布隆过滤器，拦截恶意 ID
        if (!cacheManager.mightContainBlog(blogId)) {
            log.warn("布隆过滤器拦截了恶意或不存在的 blogId 请求: {}", blogId);
            return false;
        }

        String redisKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        String hashKey = blogId.toString();

        // 1. 极速读链路：先过 HeavyKeeper + Caffeine 本地缓存，再过 Redis
        Object valueObj = cacheManager.get(redisKey, hashKey);

        if (valueObj != null) {
            ThumbRedisData redisData = valueObj instanceof ThumbRedisData
                    ? (ThumbRedisData) valueObj
                    : JSONUtil.toBean(valueObj.toString(), ThumbRedisData.class);

            // 【终极防线一】：判断是否为布隆过滤器失效后打入的“空对象 (Dummy Object)”
            if (Long.valueOf(-1L).equals(redisData.getThumbId())) {
                return false;
            }

            // 2. 内存防线：惰性删除逻辑
            if (redisData.getExpireTime() != null && new Date().after(redisData.getExpireTime())) {
                log.info("发现过期点赞数据，执行惰性删除: userId={}, blogId={}", userId, blogId);
                Thread.startVirtualThread(() -> redisTemplate.opsForHash().delete(redisKey, hashKey));
                // 过期数据不应再被视为已点赞，否则会出现前端红点与撤销接口状态不一致
                return false;
            }
            return true;
        }

        // 3. MySQL 兜底防线
        boolean exists = this.lambdaQuery()
                .eq(Thumb::getUserId, userId)
                .eq(Thumb::getBlogId, blogId)
                .exists();

        // 【终极防线二】：冷数据动态唤醒 与 防穿透空对象兜底
        ThumbRedisData targetData = new ThumbRedisData();

        if (exists) {
            // 老树开新花：把真实查到的历史冷数据拉回 Redis
            log.info("触发冷数据动态唤醒：将 MySQL 中存在的历史点赞重新载入 Redis, userId={}, blogId={}", userId, blogId);
            targetData.setExpireTime(new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000)); // 唤醒 24 小时
        } else {
            // 防穿透退化：布隆放行但库里真没有（如已被删的博客），写入极短期的空对象
            log.info("触发防穿透兜底：数据库不存在该点赞，写入短期空对象, userId={}, blogId={}", userId, blogId);
            targetData.setThumbId(-1L); // 核心标记：约定 -1 为空对象
            targetData.setExpireTime(new Date(System.currentTimeMillis() + 60 * 1000)); // 仅存活 60 秒，防止占用过多内存
        }

        // 赋予一个绝对安全的历史操作时间，防止被凌晨 2 点的对账任务误当作“在途数据”处理
        targetData.setActionTime(System.currentTimeMillis() - 60L * 60 * 1000);

        // 写回 Redis (JSON字符串) 和 本地微缓存 (Java对象)
        redisTemplate.opsForHash().put(redisKey, hashKey, JSONUtil.toJsonStr(targetData));
        cacheManager.putIfPresent(redisKey, hashKey, targetData);

        return exists;
    }

    @Override
    public BlogPageVO listUserThumbBlogs(Long userId, String cursor, int size, HttpServletRequest request) {
        int normalizedSize = normalizeThumbPageSize(size);
        ThumbCursor cursorInfo = parseThumbCursor(cursor);

        BlogPageVO result = new BlogPageVO();
        result.setSize(normalizedSize);
        result.setSort("liked");
        result.setKeyword("");
        result.setHasMore(false);
        result.setNextCursor("");

        if (userId == null) {
            return result;
        }

        Long total = this.lambdaQuery().eq(Thumb::getUserId, userId).count();
        long safeTotal = total == null ? 0L : total;
        result.setTotal(safeTotal);
        if (safeTotal <= 0) {
            return result;
        }

        List<Thumb> thumbs = this.lambdaQuery()
                .eq(Thumb::getUserId, userId)
                .and(cursorInfo != null && cursorInfo.lastId() != null && cursorInfo.lastCreateTime() != null,
                        wrapper -> wrapper.lt(Thumb::getCreateTime, cursorInfo.lastCreateTime())
                                .or(inner -> inner.eq(Thumb::getCreateTime, cursorInfo.lastCreateTime())
                                        .lt(Thumb::getId, cursorInfo.lastId())))
                .orderByDesc(Thumb::getCreateTime)
                .orderByDesc(Thumb::getId)
                .last("limit " + (normalizedSize + 1))
                .list();

        boolean hasMore = thumbs.size() > normalizedSize;
        List<Thumb> currentBatch = hasMore ? thumbs.subList(0, normalizedSize) : thumbs;
        List<BlogVO> likedBlogs = buildLikedBlogVOList(currentBatch, request);
        result.setList(likedBlogs);
        result.setHasMore(hasMore);
        if (hasMore && !currentBatch.isEmpty()) {
            result.setNextCursor(buildThumbCursor(currentBatch.getLast()));
        }
        return result;
    }

    @Override
    public ThumbActionResponse doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();

        if (this.hasThumb(blogId, userId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
        }

        Blog blog = blogService.getById(blogId);
        if (blog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "博客不存在");
        }

        long expireTimeMs = blog.getCreateTime().getTime() + (30L * 24 * 60 * 60 * 1000);
        boolean isHotData = expireTimeMs > System.currentTimeMillis();

        ThumbRedisData redisData = new ThumbRedisData();
        // 使用正值占位，列表页 hasThumb 判定依赖 thumbId>0
        redisData.setThumbId(1L);
        redisData.setActionTime(System.currentTimeMillis());

        if (isHotData) {
            redisData.setExpireTime(new Date(expireTimeMs));
        } else {
            // 冷数据也写入短期标记，防止 MQ 消费前重复点赞
            redisData.setExpireTime(new Date(System.currentTimeMillis() + 60 * 1000));
            log.info("处理冷数据点赞，直接投递 MQ: userId={}, blogId={}", userId, blogId);
        }

        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        // 用 redisTemplate 存，保证序列化格式一致
        Object existing = redisTemplate.opsForHash().get(userThumbKey, blogId.toString());
        if (existing != null) {
            ThumbRedisData existingData = JSONUtil.toBean(existing.toString(), ThumbRedisData.class);
            if (existingData.getThumbId() == null || existingData.getThumbId() != -1L) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
            }
        }
        redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), JSONUtil.toJsonStr(redisData));
        cacheManager.putIfPresent(userThumbKey, blogId.toString(), redisData);

        sendThumbEvent(userId, blogId, ThumbEvent.EventType.INCR);

        // 同步增加博客点赞数，避免首页/详情短期不一致
        blogService.lambdaUpdate()
                .eq(Blog::getId, blogId)
                .setSql("thumbCount = thumbCount + 1")
                .update();
        invalidateBlogListCache();
        blogService.evictBlogDetailCache(blogId);

        ThumbActionResponse resp = new ThumbActionResponse();
        resp.setHasThumb(true);
        resp.setThumbCount(getCurrentThumbCount(blogId));

        if (!blog.getUserId().equals(userId)) {
            Notification n = new Notification();
            n.setUserId(blog.getUserId());
            n.setFromUserId(userId);
            n.setBlogId(blogId);
            n.setType(2);
            n.setIsRead(0);
            notificationMapper.insert(n);
        }
        return resp;
    }

    @Override
    public ThumbActionResponse undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();

        String redisKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        Object valueObj = redisTemplate.opsForHash().get(redisKey, blogId.toString());
        if (valueObj != null) {
            log.info("undoThumb valueObj type={}, value={}", valueObj.getClass().getName(), valueObj);
            ThumbRedisData redisData = valueObj instanceof ThumbRedisData
                    ? (ThumbRedisData) valueObj
                    : JSONUtil.toBean(valueObj.toString(), ThumbRedisData.class);
            if (Long.valueOf(-1L).equals(redisData.getThumbId())) {
                ThumbActionResponse resp = new ThumbActionResponse();
                resp.setHasThumb(false);
                resp.setThumbCount(getCurrentThumbCount(blogId));
                return resp;
            }
        } else {
            boolean exists = this.lambdaQuery().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId).exists();
            if (!exists) {
                // Redis 与 DB 都不存在时按幂等语义处理，避免前端出现误报
                ThumbActionResponse resp = new ThumbActionResponse();
                resp.setHasThumb(false);
                resp.setThumbCount(getCurrentThumbCount(blogId));
                return resp;
            }
        }

        // 写入"已取消"标记，防止 MQ 异步消费期间穿透 MySQL 误判为已点赞
        ThumbRedisData cancelData = new ThumbRedisData();
        cancelData.setThumbId(-1L);
        cancelData.setActionTime(System.currentTimeMillis());
        cancelData.setExpireTime(new Date(System.currentTimeMillis() + 60 * 1000));
        String jsonVal = JSONUtil.toJsonStr(cancelData);
        redisTemplate.opsForHash().put(redisKey, blogId.toString(), jsonVal);
        cacheManager.putIfPresent(redisKey, blogId.toString(), cancelData);

        sendThumbEvent(userId, blogId, ThumbEvent.EventType.DECR);

        // 同步减少博客点赞数，避免首页/详情短期不一致
        blogService.lambdaUpdate()
                .eq(Blog::getId, blogId)
                .setSql("thumbCount = CASE WHEN thumbCount > 0 THEN thumbCount - 1 ELSE 0 END")
                .update();
        invalidateBlogListCache();
        blogService.evictBlogDetailCache(blogId);

        ThumbActionResponse resp = new ThumbActionResponse();
        resp.setHasThumb(false);
        resp.setThumbCount(getCurrentThumbCount(blogId));
        return resp;
    }

    private long getCurrentThumbCount(Long blogId) {
        Blog blogEntity = blogService.getById(blogId);
        return blogEntity != null && blogEntity.getThumbCount() != null ? blogEntity.getThumbCount() : 0L;
    }

    private void sendThumbEvent(Long userId, Long blogId, ThumbEvent.EventType type) {
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .blogId(blogId)
                .userId(userId)
                .type(type)
                .eventTime(LocalDateTime.now())
                .build();

        pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {
            log.error("点赞事件发送 MQ 失败, 需人工介入或重试: userId={}, blogId={}, type={}", userId, blogId, type, ex);
            return null;
        });
    }

    private void invalidateBlogListCache() {
        try {
            redisTemplate.opsForValue().increment(BLOG_LIST_VERSION_KEY);
            redisTemplate.opsForValue().increment(BLOG_HOT_VERSION_KEY);
        } catch (Exception e) {
            log.warn("点赞后更新列表缓存版本失败，跳过本次失效", e);
        }
        localCache.asMap().keySet().removeIf(k -> k.startsWith("blog:list:"));
        localCache.asMap().keySet().removeIf(k -> k.startsWith("blog:hot:"));
    }

    private List<BlogVO> buildLikedBlogVOList(List<Thumb> thumbs, HttpServletRequest request) {
        if (thumbs == null || thumbs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> blogIds = thumbs.stream().map(Thumb::getBlogId).toList();
        Map<Long, Thumb> thumbMap = thumbs.stream().collect(Collectors.toMap(Thumb::getBlogId, thumb -> thumb, (left, right) -> left));
        Map<Long, Blog> blogMap = blogService.listByIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, blog -> blog, (left, right) -> left));
        List<Blog> orderedBlogs = blogIds.stream()
                .map(blogMap::get)
                .filter(blog -> blog != null)
                .toList();
        if (orderedBlogs.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlogVO> blogVOList = blogService.getBlogVOList(orderedBlogs, request);
        blogVOList.forEach(blogVO -> {
            Thumb thumb = thumbMap.get(blogVO.getId());
            if (thumb != null) {
                blogVO.setThumbTime(thumb.getCreateTime());
            }
        });
        return blogVOList;
    }

    private int normalizeThumbPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_THUMB_PAGE_SIZE;
        }
        return Math.min(size, MAX_THUMB_PAGE_SIZE);
    }

    private ThumbCursor parseThumbCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || !cursor.startsWith(THUMB_CURSOR_PREFIX)) {
            return null;
        }
        try {
            String payload = cursor.substring(THUMB_CURSOR_PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            long timeMillis = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            return new ThumbCursor(new Date(timeMillis), id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildThumbCursor(Thumb thumb) {
        if (thumb == null || thumb.getId() == null || thumb.getCreateTime() == null) {
            return "";
        }
        return THUMB_CURSOR_PREFIX + thumb.getCreateTime().getTime() + ":" + thumb.getId();
    }

    private record ThumbCursor(Date lastCreateTime, Long lastId) {
    }
}