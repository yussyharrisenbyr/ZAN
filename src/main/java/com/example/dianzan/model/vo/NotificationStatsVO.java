package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class NotificationStatsVO {
    private long totalUnread;
    private long commentUnread;
    private long likeUnread;
    private long followUnread;
}

