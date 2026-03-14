package com.example.dianzan.service;

import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.dto.UserProfileUpdateRequest;
import com.example.dianzan.model.vo.UserProfileVO;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author linfu
* @description 针对表【user】的数据库操作Service
* @createDate 2026-02-09 12:56:25
*/
public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);

    Long userRegister(String userAccount, String username, String password, String checkPassword);

    User userLogin(String userAccount, String password, HttpServletRequest request);

    UserProfileVO getProfile(Long targetUserId, HttpServletRequest request);

    void updateMyProfile(User loginUser, UserProfileUpdateRequest request);
}
