package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class SiteOverviewVO {
    private long totalBlogs;
    private long totalUsers;
    private long totalThumbs;
    private long totalAuthors;
    private long unreadNotifications;
}

