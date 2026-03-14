package com.example.dianzan.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class CommentVO {
    private Long id;
    private Long blogId;
    private Long userId;
    private String username; // 用户名 (需要关联User表查询)
    private String userAvatar; // 头像
    private String content;
    private Integer thumbCount;
    private Date createTime;
    
    // 如果是子评论，需要知道回复了谁
    private Long replyToUserId;
    private String replyToUsername;

    // 当前一级评论下的所有二级评论列表 (嵌套在这里)
    private List<CommentVO> children = new ArrayList<>();
}