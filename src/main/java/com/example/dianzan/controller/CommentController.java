package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.model.entity.Comment;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.service.CommentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("comment")
public class CommentController {

    @Resource
    private CommentService commentService;

    @GetMapping("/list")
    public BaseResponse<Map<String, Object>> list(
            @RequestParam Long blogId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResultUtils.success(commentService.getCommentList(blogId, page, size));
    }

    @PostMapping("/add")
    public BaseResponse<Boolean> add(@RequestBody Comment comment, HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        if (loginUser == null) return ResultUtils.error(401, "请先登录");
        comment.setUserId(loginUser.getId());
        return ResultUtils.success(commentService.addComment(comment));
    }
}
