package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class UserProfileStatsVO {
    private Long userId;
    private Long blogCount;
    private Long likedAndCollectedCount;
    private Long followCount;
    private Long fansCount;
    private Long likesGivenCount;
}

