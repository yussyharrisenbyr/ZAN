package com.example.dianzan.model.dto;

import lombok.Data;

@Data
public class OssPresignRequest {

    /**
     * avatar / blogCover
     */
    private String bizType;

    private String fileName;

    private String contentType;

    private Long fileSize;
}

