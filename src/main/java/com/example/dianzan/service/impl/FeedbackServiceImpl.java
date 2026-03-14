package com.example.dianzan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianzan.exception.BusinessException;
import com.example.dianzan.exception.ErrorCode;
import com.example.dianzan.mapper.FeedbackMapper;
import com.example.dianzan.model.dto.FeedbackSubmitRequest;
import com.example.dianzan.model.entity.Feedback;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.service.FeedbackService;
import com.example.dianzan.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    private static final int MIN_CONTENT_LENGTH = 5;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_CONTACT_LENGTH = 100;
    private static final int MAX_PAGE_PATH_LENGTH = 255;

    private final UserService userService;

    @Override
    public Long submitFeedback(FeedbackSubmitRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "反馈参数不能为空");
        }
        String content = StringUtils.trimToEmpty(request.getContent());
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先填写意见内容");
        }
        if (content.length() < MIN_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "意见内容至少需要 5 个字");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "意见内容不能超过 500 个字");
        }

        String contact = StringUtils.trimToEmpty(request.getContact());
        if (contact.length() > MAX_CONTACT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "联系方式不能超过 100 个字符");
        }

        String pagePath = StringUtils.trimToEmpty(request.getPagePath());
        if (pagePath.length() > MAX_PAGE_PATH_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页面标识过长，请稍后重试");
        }

        User loginUser = userService.getLoginUser(httpServletRequest);
        Feedback feedback = new Feedback();
        feedback.setUserId(loginUser == null ? null : loginUser.getId());
        feedback.setUsernameSnapshot(loginUser == null ? null : StringUtils.trimToNull(loginUser.getUsername()));
        feedback.setContact(StringUtils.trimToNull(contact));
        feedback.setContent(content);
        feedback.setPagePath(StringUtils.defaultIfBlank(pagePath, "/"));
        feedback.setStatus(0);
        boolean saved = this.save(feedback);
        if (!saved || feedback.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        return feedback.getId();
    }
}

