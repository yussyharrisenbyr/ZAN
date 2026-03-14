package com.example.dianzan.model.dto;

import lombok.Data;

@Data
public class FeedbackSubmitRequest {
    private String content;
    private String contact;
    private String pagePath;
}

