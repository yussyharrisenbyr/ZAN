package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.mapper.NotificationMapper;
import com.example.dianzan.mapper.UserMapper;
import com.example.dianzan.model.entity.Notification;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.SiteOverviewVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.benmanes.caffeine.cache.Cache;

import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@RestController
public class MainController {

    private static final String SITE_OVERVIEW_CACHE_KEY = "site:overview";
    private static final long SITE_OVERVIEW_CACHE_TTL_SECONDS = 120;

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private NotificationMapper notificationMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private Cache<String, Object> localCache;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/site/overview")
    public BaseResponse<SiteOverviewVO> overview(HttpServletRequest request) {
        SiteOverviewVO base = getOrLoadOverviewBase();
        SiteOverviewVO result = copyOverview(base);
        result.setUnreadNotifications(countUnreadNotifications(request));
        return ResultUtils.success(result);
    }

    public void warmupOverviewCache() {
        getOrLoadOverviewBase();
    }

    private SiteOverviewVO getOrLoadOverviewBase() {
        Object localValue = localCache.getIfPresent(SITE_OVERVIEW_CACHE_KEY);
        if (localValue instanceof SiteOverviewVO siteOverviewVO) {
            return siteOverviewVO;
        }
        try {
            Object redisValue = redisTemplate.opsForValue().get(SITE_OVERVIEW_CACHE_KEY);
            if (redisValue instanceof SiteOverviewVO cached) {
                localCache.put(SITE_OVERVIEW_CACHE_KEY, cached);
                return cached;
            }
        } catch (Exception ignored) {
        }

        SiteOverviewVO overview = new SiteOverviewVO();
        Long totalBlogs = blogMapper.selectCount(null);
        Long totalUsers = userMapper.selectCount(null);
        Long totalThumbs = blogMapper.selectTotalThumbCount();
        Long totalAuthors = blogMapper.selectDistinctAuthorCount();
        overview.setTotalBlogs(totalBlogs == null ? 0L : totalBlogs);
        overview.setTotalUsers(totalUsers == null ? 0L : totalUsers);
        overview.setTotalThumbs(totalThumbs == null ? 0L : totalThumbs);
        overview.setTotalAuthors(totalAuthors == null ? 0L : totalAuthors);
        overview.setUnreadNotifications(0L);
        try {
            redisTemplate.opsForValue().set(SITE_OVERVIEW_CACHE_KEY, overview, SITE_OVERVIEW_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        localCache.put(SITE_OVERVIEW_CACHE_KEY, overview);
        return overview;
    }

    private long countUnreadNotifications(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);
        if (user == null) {
            return 0L;
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, user.getId())
                .eq(Notification::getIsRead, 0));
        return count == null ? 0L : count;
    }

    private SiteOverviewVO copyOverview(SiteOverviewVO source) {
        SiteOverviewVO target = new SiteOverviewVO();
        if (source == null) {
            return target;
        }
        target.setTotalBlogs(source.getTotalBlogs());
        target.setTotalUsers(source.getTotalUsers());
        target.setTotalThumbs(source.getTotalThumbs());
        target.setTotalAuthors(source.getTotalAuthors());
        target.setUnreadNotifications(source.getUnreadNotifications());
        return target;
    }
}
