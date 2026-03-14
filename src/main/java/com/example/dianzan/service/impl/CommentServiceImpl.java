package com.example.dianzan.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.example.dianzan.mapper.CommentMapper;
import com.example.dianzan.mapper.NotificationMapper;
import com.example.dianzan.mapper.UserMapper;
import com.example.dianzan.model.entity.Comment;
import com.example.dianzan.model.entity.Notification;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.CommentVO;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.CommentService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    @Resource
    private CommentMapper commentMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private NotificationMapper notificationMapper;
    @Lazy
    @Resource
    private BlogService blogService;

    @Override
    public Map<String, Object> getCommentList(Long blogId, int page, int size) {
        List<Comment> rootComments = commentMapper.selectPageRoots(blogId, (page - 1) * size, size);

        if (CollectionUtils.isEmpty(rootComments)) {
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0L);
            result.put("list", Collections.emptyList());
            return result;
        }

        List<Long> rootIds = rootComments.stream().map(Comment::getId).collect(Collectors.toList());
        List<Comment> subComments = commentMapper.selectByRootIds(rootIds);
        Map<Long, List<Comment>> subCommentMap = subComments.stream()
                .collect(Collectors.groupingBy(Comment::getRootId));

        // 批量查询用户信息
        Set<Long> userIds = new HashSet<>();
        rootComments.forEach(c -> userIds.add(c.getUserId()));
        subComments.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0) {
                userIds.add(c.getReplyToUserId());
            }
        });
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<CommentVO> resultList = new ArrayList<>();
        for (Comment root : rootComments) {
            CommentVO rootVO = toVO(root, userMap);
            List<Comment> children = subCommentMap.getOrDefault(root.getId(), Collections.emptyList());
            rootVO.setChildren(children.stream().map(c -> toVO(c, userMap)).collect(Collectors.toList()));
            resultList.add(rootVO);
        }

        long total = commentMapper.countRoots(blogId);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", resultList);
        return result;
    }

    @Override
    public boolean addComment(Comment comment) {
        if (comment.getRootId() == null) comment.setRootId(0L);
        if (comment.getParentId() == null) comment.setParentId(0L);
        if (comment.getReplyToUserId() == null) comment.setReplyToUserId(0L);
        boolean ok = commentMapper.insert(comment) > 0;
        if (ok) {
            var blog = blogService.getById(comment.getBlogId());
            if (blog != null && !blog.getUserId().equals(comment.getUserId())) {
                Notification n = new Notification();
                n.setUserId(blog.getUserId());
                n.setFromUserId(comment.getUserId());
                n.setBlogId(comment.getBlogId());
                n.setType(1);
                n.setIsRead(0);
                notificationMapper.insert(n);
            }
        }
        return ok;
    }

    private CommentVO toVO(Comment c, Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setBlogId(c.getBlogId());
        vo.setUserId(c.getUserId());
        vo.setContent(c.getContent());
        vo.setThumbCount(c.getThumbCount());
        vo.setCreateTime(c.getCreateTime());
        User user = userMap.get(c.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setUserAvatar(user.getAvatarUrl());
        }
        if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0) {
            vo.setReplyToUserId(c.getReplyToUserId());
            User replyUser = userMap.get(c.getReplyToUserId());
            if (replyUser != null) vo.setReplyToUsername(replyUser.getUsername());
        }
        return vo;
    }
}
