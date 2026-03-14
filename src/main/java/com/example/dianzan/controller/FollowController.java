package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.model.vo.FollowActionVO;
import com.example.dianzan.model.vo.FollowPageVO;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.service.FollowService;
import com.example.dianzan.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("follow")
public class FollowController {

    @Resource
    private FollowService followService;
    @Resource
    private UserService userService;

    @PostMapping("/do")
    public BaseResponse<FollowActionVO> doFollow(@RequestParam Long userId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            return ResultUtils.error(401, "请先登录");
        }
        return ResultUtils.success(followService.follow(loginUser.getId(), userId));
    }

    @PostMapping("/undo")
    public BaseResponse<FollowActionVO> undoFollow(@RequestParam Long userId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            return ResultUtils.error(401, "请先登录");
        }
        return ResultUtils.success(followService.unfollow(loginUser.getId(), userId));
    }

    @GetMapping("/isFollow")
    public BaseResponse<Boolean> isFollow(@RequestParam Long userId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            return ResultUtils.success(false);
        }
        boolean following = followService.isFollowing(loginUser.getId(), userId);
        return ResultUtils.success(following);
    }

    @GetMapping("/fans")
    public BaseResponse<FollowPageVO> fans(@RequestParam(required = false) Long userId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "false") boolean mutualOnly,
                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long targetUserId = resolveTargetUserId(userId, loginUser);
        if (targetUserId == null) {
            return ResultUtils.error(401, "请先登录");
        }
        FollowPageVO pageVO = followService.listFollowers(targetUserId,
                loginUser == null ? null : loginUser.getId(),
                page,
                size,
                keyword,
                mutualOnly);
        return ResultUtils.success(pageVO);
    }

    @GetMapping("/following")
    public BaseResponse<FollowPageVO> following(@RequestParam(required = false) Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "false") boolean mutualOnly,
                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long targetUserId = resolveTargetUserId(userId, loginUser);
        if (targetUserId == null) {
            return ResultUtils.error(401, "请先登录");
        }
        FollowPageVO pageVO = followService.listFollowees(targetUserId,
                loginUser == null ? null : loginUser.getId(),
                page,
                size,
                keyword,
                mutualOnly);
        return ResultUtils.success(pageVO);
    }

    private Long resolveTargetUserId(Long userId, User loginUser) {
        if (userId != null) {
            return userId;
        }
        return loginUser == null ? null : loginUser.getId();
    }
}

