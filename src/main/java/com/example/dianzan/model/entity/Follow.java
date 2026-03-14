package com.example.dianzan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("follow")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者（粉丝） */
    private Long followerId;

    /** 被关注者 */
    private Long followeeId;

    private Date createTime;
}

