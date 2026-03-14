package com.example.dianzan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName(value = "favorite")
public class Favorite {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long blogId;

    private Date createTime;
}

