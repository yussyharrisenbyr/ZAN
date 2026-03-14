package com.example.dianzan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String usernameSnapshot;

    private String contact;

    private String content;

    private String pagePath;

    /** 0=待处理 1=已处理 */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}

