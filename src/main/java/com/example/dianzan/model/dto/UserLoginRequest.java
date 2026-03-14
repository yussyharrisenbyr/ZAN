package com.example.dianzan.model.dto;

import lombok.Data;

@Data
public class UserLoginRequest {
    private String userAccount;
    private String password;
}
