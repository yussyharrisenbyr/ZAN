package com.example.dianzan.service.impl;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.example.dianzan.config.OssProperties;
import com.example.dianzan.exception.BusinessException;
import com.example.dianzan.exception.ErrorCode;
import com.example.dianzan.model.dto.OssPresignRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.OssPresignVO;
import com.example.dianzan.service.OssUploadService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OssUploadServiceImpl implements OssUploadService {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    @Autowired
    private ObjectProvider<OSS> ossClientProvider;

    @Resource
    private OssProperties ossProperties;

    @Override
    public OssPresignVO createPresignedUpload(User loginUser, OssPresignRequest request) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        validateOssConfig();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传参数不能为空");
        }

        String bizType = StringUtils.trimToEmpty(request.getBizType());
        String fileName = StringUtils.trimToEmpty(request.getFileName());
        String contentType = normalizeContentType(request.getContentType());
        long fileSize = request.getFileSize() == null ? 0L : request.getFileSize();

        if (StringUtils.isBlank(fileName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        if (fileSize <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小非法");
        }
        if (fileSize > ossProperties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    String.format("图片大小不能超过 %.1f MB", ossProperties.getMaxFileSizeBytes() / 1024D / 1024D));
        }
        validateContentType(contentType);

        String objectKey = buildObjectKey(loginUser.getId(), bizType, fileName, contentType);
        long expireAt = System.currentTimeMillis() + Math.max(ossProperties.getSignExpireSeconds(), 60L) * 1000L;
        Date expiration = new Date(expireAt);

        GeneratePresignedUrlRequest presignedUrlRequest = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(),
                objectKey,
                HttpMethod.PUT
        );
        presignedUrlRequest.setExpiration(expiration);
        presignedUrlRequest.setContentType(contentType);

        OSS ossClient = resolveOssClient();
        URL uploadUrl = ossClient.generatePresignedUrl(presignedUrlRequest);

        OssPresignVO result = new OssPresignVO();
        result.setMethod(HttpMethod.PUT.name());
        result.setContentType(contentType);
        result.setObjectKey(objectKey);
        result.setUploadUrl(uploadUrl.toString());
        result.setPublicUrl(buildPublicUrl(objectKey));
        result.setExpireAt(expireAt);
        return result;
    }

    private void validateOssConfig() {
        if (StringUtils.isAnyBlank(ossProperties.getEndpoint(), ossProperties.getRegion(), ossProperties.getBucketName())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 未完成配置，请先设置 endpoint / region / bucketName");
        }
    }

    private OSS resolveOssClient() {
        OSS ossClient = ossClientProvider.getIfAvailable();
        if (ossClient == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 客户端未初始化，请先检查 endpoint 与凭证配置");
        }
        return ossClient;
    }

    private void validateContentType(String contentType) {
        List<String> allowedContentTypes = ossProperties.getAllowedContentTypes();
        boolean inConfiguredList = allowedContentTypes == null || allowedContentTypes.isEmpty() || allowedContentTypes.contains(contentType);
        if (!IMAGE_CONTENT_TYPES.contains(contentType) || !inConfiguredList) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 JPG、PNG、WEBP、GIF 图片");
        }
    }

    private String buildObjectKey(Long userId, String bizType, String fileName, String contentType) {
        String folder = switch (bizType) {
            case "avatar" -> trimFolder(ossProperties.getAvatarDir());
            case "blogCover" -> trimFolder(ossProperties.getBlogCoverDir());
            case "blogImage" -> trimFolder(ossProperties.getBlogImageDir());
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的上传类型");
        };
        String extension = resolveExtension(fileName, contentType);
        LocalDate today = LocalDate.now();
        return String.format(Locale.ROOT,
                "%s/%04d/%02d/%02d/%d-%s.%s",
                folder,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                userId,
                UUID.randomUUID().toString().replace("-", ""),
                extension);
    }

    private String trimFolder(String folder) {
        String normalized = StringUtils.defaultIfBlank(folder, "uploads").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String resolveExtension(String fileName, String contentType) {
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            ext = fileName.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.isNotBlank(ext)) {
            if (ext.equals("jpg") || ext.equals("jpeg")) return "jpg";
            if (ext.equals("png")) return "png";
            if (ext.equals("webp")) return "webp";
            if (ext.equals("gif")) return "gif";
        }
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.defaultIfBlank(contentType, "image/jpeg").trim().toLowerCase(Locale.ROOT);
    }

    private String buildPublicUrl(String objectKey) {
        String configuredBaseUrl = StringUtils.trimToNull(ossProperties.getPublicBaseUrl());
        if (configuredBaseUrl != null) {
            return configuredBaseUrl.replaceAll("/+$", "") + "/" + objectKey;
        }
        try {
            URI endpointUri = URI.create(ossProperties.getEndpoint());
            String scheme = StringUtils.defaultIfBlank(endpointUri.getScheme(), "https");
            String host = endpointUri.getHost();
            if (StringUtils.isBlank(host)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS endpoint 配置不合法");
            }
            return String.format("%s://%s.%s/%s", scheme, ossProperties.getBucketName(), host, objectKey);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS endpoint 配置不合法");
        }
    }
}

