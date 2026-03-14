package com.example.dianzan;

import com.example.dianzan.mapper.FollowMapper;
import com.example.dianzan.mapper.NotificationMapper;
import com.example.dianzan.mapper.UserMapper;
import com.example.dianzan.model.entity.Notification;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.FollowActionVO;
import com.example.dianzan.model.vo.FollowPageVO;
import com.example.dianzan.model.vo.FollowUserVO;
import com.example.dianzan.service.impl.FollowServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowServiceImplTest {

    private FollowMapper followMapper;
    private UserMapper userMapper;
    private NotificationMapper notificationMapper;
    private Cache<String, Object> localCache;
    private FollowServiceImpl followService;

    @BeforeEach
    void setUp() {
        followMapper = mock(FollowMapper.class);
        userMapper = mock(UserMapper.class);
        notificationMapper = mock(NotificationMapper.class);
        localCache = Caffeine.newBuilder().maximumSize(100).build();
        followService = new FollowServiceImpl();
        ReflectionTestUtils.setField(followService, "followMapper", followMapper);
        ReflectionTestUtils.setField(followService, "userMapper", userMapper);
        ReflectionTestUtils.setField(followService, "notificationMapper", notificationMapper);
        ReflectionTestUtils.setField(followService, "localCache", localCache);
    }

    @Test
    void cachedFollowCountsShouldReuseLocalCache() {
        when(followMapper.selectFolloweeCount(1L)).thenReturn(3L);

        long first = followService.countFollowees(1L);
        long second = followService.countFollowees(1L);

        assertThat(first).isEqualTo(3L);
        assertThat(second).isEqualTo(3L);
        verify(followMapper, times(1)).selectFolloweeCount(1L);
    }

    @Test
    void followShouldInvalidateCachedCountsAndReturnFreshNumbers() {
        User targetUser = new User();
        targetUser.setId(2L);
        when(userMapper.selectById(2L)).thenReturn(targetUser);
        when(followMapper.selectCount(any())).thenReturn(0L);
        when(followMapper.selectFolloweeCount(1L)).thenReturn(1L, 2L);
        when(followMapper.selectFollowerCount(2L)).thenReturn(5L);

        assertThat(followService.countFollowees(1L)).isEqualTo(1L);

        FollowActionVO result = followService.follow(1L, 2L);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        assertThat(result.isFollowing()).isTrue();
        assertThat(result.getFollowCount()).isEqualTo(2L);
        assertThat(result.getFansCount()).isEqualTo(5L);
        verify(followMapper, times(2)).selectFolloweeCount(1L);
        verify(notificationMapper).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getBlogId()).isEqualTo(0L);
    }

    @Test
    void followListShouldExposeMutualFollowFlags() {
        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("互关用户");
        user2.setUserAccount("13800000002");
        User user3 = new User();
        user3.setId(3L);
        user3.setUsername("单向关注");
        user3.setUserAccount("13800000003");

        when(followMapper.countFolloweeUsers(1L, null, 1L, false)).thenReturn(2L);
        when(followMapper.selectFolloweeIdsPage(1L, null, 1L, false, 0, 10)).thenReturn(List.of(2L, 3L));
        when(followMapper.selectExistingFolloweeIds(1L, List.of(2L, 3L))).thenReturn(List.of(2L, 3L));
        when(followMapper.selectExistingFollowerIds(1L, List.of(2L, 3L))).thenReturn(List.of(2L));
        when(userMapper.selectBatchIds(List.of(2L, 3L))).thenReturn(List.of(user2, user3));

        FollowPageVO page = followService.listFollowees(1L, 1L, 1, 10, null, false);
        List<FollowUserVO> result = page.getList();

        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).isFollowing()).isTrue();
        assertThat(result.get(0).isFollowingViewer()).isTrue();
        assertThat(result.get(0).isMutualFollow()).isTrue();
        assertThat(result.get(1).isFollowing()).isTrue();
        assertThat(result.get(1).isFollowingViewer()).isFalse();
        assertThat(result.get(1).isMutualFollow()).isFalse();
    }

    @Test
    void followListShouldNormalizeKeywordAndPageSize() {
        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("分页用户");
        user2.setUserAccount("13800000002");

        when(followMapper.countFollowerUsers(9L, "分页", null, false)).thenReturn(1L);
        when(followMapper.selectFollowerIdsPage(9L, "分页", null, false, 0, 50)).thenReturn(List.of(2L));
        when(userMapper.selectBatchIds(List.of(2L))).thenReturn(List.of(user2));

        FollowPageVO page = followService.listFollowers(9L, null, 0, 99, "  分页  ", false);

        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(50);
        assertThat(page.getKeyword()).isEqualTo("分页");
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList()).hasSize(1);
        verify(followMapper).countFollowerUsers(9L, "分页", null, false);
        verify(followMapper).selectFollowerIdsPage(9L, "分页", null, false, 0, 50);
    }

    @Test
    void anonymousMutualOnlyQueryShouldReturnEmptyPage() {
        when(followMapper.countFolloweeUsers(5L, null, null, true)).thenReturn(0L);

        FollowPageVO page = followService.listFollowees(5L, null, 1, 10, null, true);

        assertThat(page.isMutualOnly()).isTrue();
        assertThat(page.getTotal()).isZero();
        assertThat(page.getList()).isEmpty();
        verify(followMapper).countFolloweeUsers(5L, null, null, true);
    }
}

