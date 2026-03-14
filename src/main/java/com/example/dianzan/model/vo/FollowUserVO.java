package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class FollowUserVO {
    private Long userId;
    private String username;
    private String userAccount;
    private String avatarUrl;
    private String avatarText;
    private String avatarBg;
    private boolean self;
    private boolean following;
    private boolean followingViewer;
    private boolean mutualFollow;
}

