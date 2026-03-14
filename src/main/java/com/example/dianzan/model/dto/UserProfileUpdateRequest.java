package com.example.dianzan.model.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private Integer age;
    private String bio;
    private String avatarUrl;
}

