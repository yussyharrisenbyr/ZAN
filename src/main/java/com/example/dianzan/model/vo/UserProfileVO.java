package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class UserProfileVO {
    private Long userId;
    private String username;
    private boolean self;
    private boolean following;
    private String avatarUrl;
    private String avatarText;
    private String avatarBg;
    private String bio;
    private Integer age;

    private long followCount;
    private long fansCount;
    private long likedAndCollectedCount;
    private long blogCount;
    private long likesGivenCount;
    private long notesCount;
}

