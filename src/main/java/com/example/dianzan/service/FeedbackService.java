package com.example.dianzan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dianzan.model.dto.FeedbackSubmitRequest;
import com.example.dianzan.model.entity.Feedback;
import jakarta.servlet.http.HttpServletRequest;

public interface FeedbackService extends IService<Feedback> {
    Long submitFeedback(FeedbackSubmitRequest request, HttpServletRequest httpServletRequest);
}

