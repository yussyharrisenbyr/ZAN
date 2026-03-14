package com.example.dianzan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@TableName("comment")
@Data
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long blogId;
    private Long userId;
    private String content;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private Integer thumbCount;
    private Date createTime;
}
