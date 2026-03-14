package com.example.dianzan.model.vo;

import lombok.Data;

import java.util.Date;

@Data
public class NotificationItemVO {
    private Long id;
    private Integer type;
    private Integer isRead;
    private Date createTime;
    private Long fromUserId;
    private String fromUsername;
    private String fromUserAvatarUrl;
    private Long blogId;
    private String blogTitle;
}

