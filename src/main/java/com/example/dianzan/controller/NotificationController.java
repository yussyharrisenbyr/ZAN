package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.mapper.NotificationMapper;
import com.example.dianzan.mapper.UserMapper;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.entity.Notification;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.NotificationItemVO;
import com.example.dianzan.model.vo.NotificationPageVO;
import com.example.dianzan.model.vo.NotificationStatsVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@RestController
@RequestMapping("notification")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private NotificationMapper notificationMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private BlogMapper blogMapper;

    @GetMapping("/unread/count")
    public BaseResponse<Long> unreadCount(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);
        if (user == null) return ResultUtils.success(0L);
        return ResultUtils.success(countUnreadByType(user.getId(), null));
    }

    @GetMapping("/stats")
    public BaseResponse<NotificationStatsVO> stats(HttpServletRequest request) {
        User user = getLoginUser(request);
        NotificationStatsVO vo = new NotificationStatsVO();
        if (user == null) {
            return ResultUtils.success(vo);
        }
        vo.setCommentUnread(countUnreadByType(user.getId(), 1));
        vo.setLikeUnread(countUnreadByType(user.getId(), 2));
        vo.setFollowUnread(countUnreadByType(user.getId(), 3));
        vo.setTotalUnread(vo.getCommentUnread() + vo.getLikeUnread() + vo.getFollowUnread());
        return ResultUtils.success(vo);
    }

    @GetMapping("/page")
    public BaseResponse<NotificationPageVO> page(@RequestParam Integer type,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(defaultValue = "false") boolean onlyUnread,
                                                 HttpServletRequest request) {
        User user = getLoginUser(request);
        NotificationPageVO vo = new NotificationPageVO();
        vo.setPage(Math.max(page, 1));
        vo.setSize(normalizePageSize(size));
        if (user == null || !isValidType(type)) {
            return ResultUtils.success(vo);
        }

        LambdaQueryWrapper<Notification> countWrapper = buildPageWrapper(user.getId(), type, onlyUnread);
        long total = notificationMapper.selectCount(countWrapper);
        vo.setTotal(total);
        if (total <= 0) {
            return ResultUtils.success(vo);
        }

        int offset = (vo.getPage() - 1) * vo.getSize();
        LambdaQueryWrapper<Notification> listWrapper = buildPageWrapper(user.getId(), type, onlyUnread)
                .last("limit " + offset + "," + vo.getSize());
        List<Notification> list = notificationMapper.selectList(listWrapper);
        vo.setList(toItemVOList(list));
        return ResultUtils.success(vo);
    }

    @GetMapping("/list")
    public BaseResponse<List<Notification>> list(HttpServletRequest request) {
        User user = getLoginUser(request);
        if (user == null) return ResultUtils.success(List.of());
        List<Notification> list = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, user.getId())
                        .orderByDesc(Notification::getCreateTime));
        return ResultUtils.success(list);
    }

    @PostMapping("/read")
    public BaseResponse<Boolean> read(@RequestParam Long notificationId, HttpServletRequest request) {
        User user = getLoginUser(request);
        if (user == null || notificationId == null) return ResultUtils.success(false);
        Notification update = new Notification();
        update.setIsRead(1);
        int rows = notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getUserId, user.getId())
                        .eq(Notification::getIsRead, 0));
        return ResultUtils.success(rows > 0);
    }

    @PostMapping("/read/all")
    public BaseResponse<Boolean> readAll(@RequestParam(required = false) Integer type, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);
        if (user == null) return ResultUtils.success(false);
        Notification update = new Notification();
        update.setIsRead(1);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, user.getId())
                .eq(Notification::getIsRead, 0);
        if (isValidType(type)) {
            wrapper.eq(Notification::getType, type);
        }
        notificationMapper.update(update, wrapper);
        return ResultUtils.success(true);
    }

    private User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);
    }

    private long countUnreadByType(Long userId, Integer type) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0);
        if (type != null) {
            wrapper.eq(Notification::getType, type);
        }
        Long count = notificationMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private LambdaQueryWrapper<Notification> buildPageWrapper(Long userId, Integer type, boolean onlyUnread) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, type);
        if (onlyUnread) {
            wrapper.eq(Notification::getIsRead, 0);
        }
        return wrapper.orderByAsc(Notification::getIsRead).orderByDesc(Notification::getCreateTime);
    }

    private List<NotificationItemVO> toItemVOList(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> fromUserIds = notifications.stream()
                .map(Notification::getFromUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, User> userMap = fromUserIds.isEmpty() ? Collections.emptyMap() : userMapper.selectBatchIds(fromUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a, HashMap::new));

        Set<Long> blogIds = notifications.stream()
                .map(Notification::getBlogId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> blogTitleMap = blogIds.isEmpty() ? Collections.emptyMap() : blogMapper.selectBatchIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, Blog::getTitle, (a, b) -> a, HashMap::new));

        return notifications.stream().map(n -> {
            NotificationItemVO vo = new NotificationItemVO();
            vo.setId(n.getId());
            vo.setType(n.getType());
            vo.setIsRead(n.getIsRead());
            vo.setCreateTime(n.getCreateTime());
            vo.setFromUserId(n.getFromUserId());
            User fromUser = userMap.get(n.getFromUserId());
            vo.setFromUsername(fromUser != null ? fromUser.getUsername() : (n.getFromUserId() == null ? "系统" : "用户" + n.getFromUserId()));
            vo.setFromUserAvatarUrl(fromUser == null ? null : fromUser.getAvatarUrl());
            vo.setBlogId(n.getBlogId());
            vo.setBlogTitle(blogTitleMap.getOrDefault(n.getBlogId(), n.getBlogId() == null ? "" : "博客 #" + n.getBlogId()));
            return vo;
        }).collect(Collectors.toList());
    }

    private boolean isValidType(Integer type) {
        return type != null && type >= 1 && type <= 3;
    }

    private int normalizePageSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
