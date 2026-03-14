package com.example.dianzan.service;

import com.example.dianzan.model.entity.Comment;

import java.util.Map;

public interface CommentService {
    Map<String, Object> getCommentList(Long blogId, int page, int size);
    boolean addComment(Comment comment);
}
