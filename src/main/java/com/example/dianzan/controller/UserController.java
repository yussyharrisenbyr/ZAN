package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.model.dto.UserLoginRequest;
import com.example.dianzan.model.dto.UserProfileUpdateRequest;
import com.example.dianzan.model.dto.UserRegisterRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.UserProfileVO;
import com.example.dianzan.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("user")
public class UserController {
    @Resource
    private UserService userService;

    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            return ResultUtils.error(400, "参数为空");
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String username = userRegisterRequest.getUsername();
        String password = userRegisterRequest.getPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, username, password, checkPassword);
        return ResultUtils.success(result);
    }

    @PostMapping("/login")
    public BaseResponse<User> login(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            return ResultUtils.error(400, "参数为空");
        }
        String userAccount = userLoginRequest.getUserAccount();
        String password = userLoginRequest.getPassword();
        User user = userService.userLogin(userAccount, password, request);
        return ResultUtils.success(user);
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResultUtils.success(true);
    }

    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User loginUser = session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);
        return ResultUtils.success(loginUser);
    }

    @GetMapping("/profile")
    public BaseResponse<UserProfileVO> profile(@RequestParam(value = "userId", required = false) Long userId,
                                               HttpServletRequest request) {
        return ResultUtils.success(userService.getProfile(userId, request));
    }

    @PostMapping("/me/profile")
    public BaseResponse<UserProfileVO> updateMyProfile(@RequestBody UserProfileUpdateRequest updateRequest,
                                                       HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            return ResultUtils.error(401, "请先登录");
        }
        userService.updateMyProfile(loginUser, updateRequest);
        return ResultUtils.success(userService.getProfile(loginUser.getId(), request));
    }
}
