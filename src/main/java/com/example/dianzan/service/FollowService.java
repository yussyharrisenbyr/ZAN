package com.example.dianzan.service;

import com.example.dianzan.model.vo.FollowActionVO;
import com.example.dianzan.model.vo.FollowPageVO;
import com.example.dianzan.model.vo.FollowUserVO;

public interface FollowService {

    FollowActionVO follow(Long followerId, Long followeeId);

    FollowActionVO unfollow(Long followerId, Long followeeId);

    long countFollowees(Long userId);

    long countFollowers(Long userId);

    boolean isFollowing(Long followerId, Long followeeId);

    FollowPageVO listFollowees(Long userId, Long viewerId, int page, int size, String keyword, boolean mutualOnly);

    FollowPageVO listFollowers(Long userId, Long viewerId, int page, int size, String keyword, boolean mutualOnly);
}

