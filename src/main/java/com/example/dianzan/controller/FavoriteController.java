package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.model.dto.favorite.DoFavoriteRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.FavoriteActionResponse;
import com.example.dianzan.service.FavoriteService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.dianzan.constant.UserConstant.LOGIN_USER;

@RestController
@RequestMapping("favorite")
public class FavoriteController {
    @Resource
    private FavoriteService favoriteService;

    @GetMapping("/user")
    public BaseResponse<BlogPageVO> listByUser(@RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "15") int size,
                                               HttpServletRequest request) {
        Long targetUserId = userId;
        if (targetUserId == null) {
            User loginUser = (User) request.getSession().getAttribute(LOGIN_USER);
            if (loginUser == null) {
                return ResultUtils.error(40100, "请先登录");
            }
            targetUserId = loginUser.getId();
        }
        return ResultUtils.success(favoriteService.listUserFavoriteBlogs(targetUserId, cursor, size, request));
    }

    @PostMapping("/do")
    public BaseResponse<FavoriteActionResponse> doFavorite(@RequestBody DoFavoriteRequest request,
                                                           HttpServletRequest httpServletRequest) {
        return ResultUtils.success(favoriteService.doFavorite(request, httpServletRequest));
    }

    @PostMapping("/undo")
    public BaseResponse<FavoriteActionResponse> undoFavorite(@RequestBody DoFavoriteRequest request,
                                                             HttpServletRequest httpServletRequest) {
        return ResultUtils.success(favoriteService.undoFavorite(request, httpServletRequest));
    }
}

