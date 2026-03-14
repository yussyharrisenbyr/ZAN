package com.example.dianzan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dianzan.exception.BusinessException;
import com.example.dianzan.exception.ErrorCode;
import com.example.dianzan.mapper.FollowMapper;
import com.example.dianzan.mapper.UserMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.example.dianzan.model.entity.Follow;
import com.example.dianzan.model.entity.Notification;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.FollowActionVO;
import com.example.dianzan.model.vo.FollowPageVO;
import com.example.dianzan.model.vo.FollowUserVO;
import com.example.dianzan.service.FollowService;
import com.example.dianzan.mapper.NotificationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl implements FollowService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String DEFAULT_AVATAR_BG = "#1e80ff";
    private static final long FOLLOW_NOTIFICATION_BLOG_ID = 0L;
    private static final String FOLLOWEE_COUNT_CACHE_KEY_PREFIX = "follow:count:followee:";
    private static final String FOLLOWER_COUNT_CACHE_KEY_PREFIX = "follow:count:follower:";

    @Resource
    private FollowMapper followMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private Cache<String, Object> localCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowActionVO follow(Long followerId, Long followeeId) {
        validateFollowParams(followerId, followeeId);
        if (userMapper.selectById(followeeId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目标用户不存在");
        }
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        if (count == null || count <= 0) {
            Follow f = new Follow();
            f.setFollowerId(followerId);
            f.setFolloweeId(followeeId);
            f.setCreateTime(new Date());
            followMapper.insert(f);

            Notification n = new Notification();
            n.setUserId(followeeId);
            n.setFromUserId(followerId);
            n.setBlogId(FOLLOW_NOTIFICATION_BLOG_ID);
            n.setType(3);
            n.setIsRead(0);
            n.setCreateTime(new Date());
            notificationMapper.insert(n);
        }
        invalidateCountCache(followerId, followeeId);
        return buildFollowActionVO(followerId, followeeId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowActionVO unfollow(Long followerId, Long followeeId) {
        validateFollowParams(followerId, followeeId);
        if (userMapper.selectById(followeeId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目标用户不存在");
        }
        followMapper.deleteByFollowerAndFollowee(followerId, followeeId);
        invalidateCountCache(followerId, followeeId);
        return buildFollowActionVO(followerId, followeeId, false);
    }

    @Override
    public long countFollowees(Long userId) {
        return getCachedCount(FOLLOWEE_COUNT_CACHE_KEY_PREFIX, userId,
                () -> followMapper.selectFolloweeCount(userId));
    }

    @Override
    public long countFollowers(Long userId) {
        return getCachedCount(FOLLOWER_COUNT_CACHE_KEY_PREFIX, userId,
                () -> followMapper.selectFollowerCount(userId));
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            return false;
        }
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        return count != null && count > 0;
    }

    @Override
    public FollowPageVO listFollowees(Long userId, Long viewerId, int page, int size, String keyword, boolean mutualOnly) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String normalizedKeyword = normalizeKeyword(keyword);
        FollowPageVO result = createEmptyPage(normalizedPage, normalizedSize, normalizedKeyword, mutualOnly);
        if (userId == null) {
            return result;
        }
        Long total = followMapper.countFolloweeUsers(userId, normalizedKeyword, viewerId, mutualOnly);
        result.setTotal(total == null ? 0L : total);
        if (result.getTotal() <= 0) {
            return result;
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Long> ids = followMapper.selectFolloweeIdsPage(userId, normalizedKeyword, viewerId, mutualOnly, offset, normalizedSize);
        result.setList(buildFollowUserList(ids, viewerId));
        return result;
    }

    @Override
    public FollowPageVO listFollowers(Long userId, Long viewerId, int page, int size, String keyword, boolean mutualOnly) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String normalizedKeyword = normalizeKeyword(keyword);
        FollowPageVO result = createEmptyPage(normalizedPage, normalizedSize, normalizedKeyword, mutualOnly);
        if (userId == null) {
            return result;
        }
        Long total = followMapper.countFollowerUsers(userId, normalizedKeyword, viewerId, mutualOnly);
        result.setTotal(total == null ? 0L : total);
        if (result.getTotal() <= 0) {
            return result;
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Long> ids = followMapper.selectFollowerIdsPage(userId, normalizedKeyword, viewerId, mutualOnly, offset, normalizedSize);
        result.setList(buildFollowUserList(ids, viewerId));
        return result;
    }

    private void validateFollowParams(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户参数不能为空");
        }
        if (Objects.equals(followerId, followeeId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能关注自己");
        }
    }

    private FollowActionVO buildFollowActionVO(Long followerId, Long followeeId, boolean following) {
        FollowActionVO vo = new FollowActionVO();
        vo.setTargetUserId(followeeId);
        vo.setFollowing(following);
        vo.setFollowCount(countFollowees(followerId));
        vo.setFansCount(countFollowers(followeeId));
        return vo;
    }

    private FollowPageVO createEmptyPage(int page, int size, String keyword, boolean mutualOnly) {
        FollowPageVO vo = new FollowPageVO();
        vo.setPage(page);
        vo.setSize(size);
        vo.setKeyword(keyword == null ? "" : keyword);
        vo.setMutualOnly(mutualOnly);
        return vo;
    }

    private List<FollowUserVO> buildFollowUserList(List<Long> ids, Long viewerId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Set<Long> viewerFollowingIds = viewerId == null
                ? Collections.emptySet()
                : new HashSet<>(followMapper.selectExistingFolloweeIds(viewerId, ids));
        Set<Long> viewerFollowerIds = viewerId == null
                ? Collections.emptySet()
                : new HashSet<>(followMapper.selectExistingFollowerIds(viewerId, ids));
        return ids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(user -> toFollowUserVO(user, viewerId, viewerFollowingIds, viewerFollowerIds))
                .collect(Collectors.toList());
    }

    private FollowUserVO toFollowUserVO(User user, Long viewerId, Set<Long> viewerFollowingIds, Set<Long> viewerFollowerIds) {
        FollowUserVO vo = new FollowUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setUserAccount(user.getUserAccount());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setAvatarText(buildAvatarText(user));
        vo.setAvatarBg(pickAvatarBg());
        vo.setSelf(viewerId != null && Objects.equals(viewerId, user.getId()));
        boolean self = vo.isSelf();
        boolean following = viewerId != null && viewerFollowingIds.contains(user.getId()) && !self;
        boolean followingViewer = viewerId != null && viewerFollowerIds.contains(user.getId()) && !self;
        vo.setFollowing(following);
        vo.setFollowingViewer(followingViewer);
        vo.setMutualFollow(following && followingViewer);
        return vo;
    }

    private long getCachedCount(String prefix, Long userId, Supplier<Long> loader) {
        if (userId == null) {
            return 0L;
        }
        String cacheKey = prefix + userId;
        Object cached = localCache.getIfPresent(cacheKey);
        if (cached instanceof Number number) {
            return number.longValue();
        }
        Long loaded = loader.get();
        long count = loaded == null ? 0L : loaded;
        localCache.put(cacheKey, count);
        return count;
    }


    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    private void invalidateCountCache(Long followerId, Long followeeId) {
        if (followerId != null) {
            localCache.invalidate(FOLLOWEE_COUNT_CACHE_KEY_PREFIX + followerId);
            localCache.invalidate(FOLLOWER_COUNT_CACHE_KEY_PREFIX + followerId);
        }
        if (followeeId != null) {
            localCache.invalidate(FOLLOWEE_COUNT_CACHE_KEY_PREFIX + followeeId);
            localCache.invalidate(FOLLOWER_COUNT_CACHE_KEY_PREFIX + followeeId);
        }
    }

    private String buildAvatarText(User user) {
        String username = user.getUsername();
        if (username != null && !username.isBlank()) {
            return username.substring(0, 1).toUpperCase();
        }
        String account = user.getUserAccount();
        return (account == null || account.isBlank()) ? "U" : account.substring(0, 1).toUpperCase();
    }

    private String pickAvatarBg() {
        return DEFAULT_AVATAR_BG;
    }
}

