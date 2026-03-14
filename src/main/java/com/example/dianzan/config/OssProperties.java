package com.example.dianzan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    private String endpoint;

    private String region;

    private String bucketName;

    private String accessKeyId;

    private String accessKeySecret;

    /**
     * 对外访问的基础域名，例如 https://your-bucket.oss-cn-hangzhou.aliyuncs.com
     * 若未配置，则会根据 endpoint + bucketName 推导。
     */
    private String publicBaseUrl;

    private long signExpireSeconds = 600;

    private String avatarDir = "avatars";

    private String blogCoverDir = "blog-covers";

    private String blogImageDir = "blog-images";

    private long maxFileSizeBytes = 5 * 1024 * 1024L;

    private List<String> allowedContentTypes = new ArrayList<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    ));
}

