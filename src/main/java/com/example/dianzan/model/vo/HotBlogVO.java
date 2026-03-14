package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class HotBlogVO {
    private Long blogId;
    private String title;
    private String coverImg;
    private Long userId;
    private String authorName;
    private String authorAvatarUrl;
    private Long weeklyThumbCount;
    private Integer thumbCount;
    private Boolean hasThumb;
}

