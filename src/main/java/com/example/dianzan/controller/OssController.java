package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.model.dto.OssPresignRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.OssPresignVO;
import com.example.dianzan.service.OssUploadService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("oss")
public class OssController {

    @Resource
    private OssUploadService ossUploadService;

    @PostMapping("/presign")
    public BaseResponse<OssPresignVO> createPresignedUpload(@RequestBody OssPresignRequest request,
                                                            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getSession().getAttribute(UserConstant.LOGIN_USER);
        if (loginUser == null) {
            return ResultUtils.error(401, "请先登录");
        }
        return ResultUtils.success(ossUploadService.createPresignedUpload(loginUser, request));
    }
}

