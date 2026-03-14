package com.example.dianzan.model.vo;

import lombok.Data;

@Data
public class OssPresignVO {

    private String method;

    private String contentType;

    private String uploadUrl;

    private String objectKey;

    private String publicUrl;

    private long expireAt;
}

