package com.example.dianzan.service;

import com.example.dianzan.model.dto.OssPresignRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.OssPresignVO;

public interface OssUploadService {

    OssPresignVO createPresignedUpload(User loginUser, OssPresignRequest request);
}

