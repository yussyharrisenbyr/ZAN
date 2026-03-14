package com.example.dianzan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录账号，要求 11 位纯数字
     */
    private String userAccount;

    /**
     * 
     */
    private String username;

    /**
     *
     */
    private String password;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 个人简介
     */
    private String bio;

    private Integer role;
}