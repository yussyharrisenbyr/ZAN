package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class FollowActionVO {
    private Long targetUserId;
    private boolean following;
    private long followCount;
    private long fansCount;
}

