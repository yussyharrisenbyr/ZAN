package com.example.dianzan.model.dto;

import lombok.Data;

@Data
public class UserRegisterRequest {
    private String userAccount;
    private String username;
    private String password;
    private String checkPassword;
}
