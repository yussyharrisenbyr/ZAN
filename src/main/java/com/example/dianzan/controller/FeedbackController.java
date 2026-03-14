package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.model.dto.FeedbackSubmitRequest;
import com.example.dianzan.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/submit")
    public BaseResponse<Long> submit(@RequestBody FeedbackSubmitRequest submitRequest,
                                     HttpServletRequest request) {
        return ResultUtils.success(feedbackService.submitFeedback(submitRequest, request));
    }
}

