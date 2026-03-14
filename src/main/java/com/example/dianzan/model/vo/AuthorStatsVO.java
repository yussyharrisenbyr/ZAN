package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class AuthorStatsVO {
    private Long userId;
    private Long articleCount;
    private Long likeCount;
    private Long favoriteCount;
}

