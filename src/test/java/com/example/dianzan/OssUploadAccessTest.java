package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.controller.OssController;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.OssPresignVO;
import com.example.dianzan.service.OssUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        OssUploadAccessTest.TestApplication.class,
        OssController.class,
        CorsConfig.class,
        OssUploadAccessTest.MockConfig.class
})
class OssUploadAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        OssUploadService ossUploadService() {
            return mock(OssUploadService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OssUploadService ossUploadService;

    private User loginUser;
    private OssPresignVO presignVO;

    @BeforeEach
    void setUp() {
        reset(ossUploadService);

        loginUser = new User();
        loginUser.setId(1L);
        loginUser.setUserAccount("13800000001");
        loginUser.setUsername("测试用户");

        presignVO = new OssPresignVO();
        presignVO.setMethod("PUT");
        presignVO.setContentType("image/png");
        presignVO.setObjectKey("avatars/2026/03/13/1-demo.png");
        presignVO.setUploadUrl("https://example-bucket.oss-cn-hangzhou.aliyuncs.com/avatars/2026/03/13/1-demo.png?signature=demo");
        presignVO.setPublicUrl("https://example-bucket.oss-cn-hangzhou.aliyuncs.com/avatars/2026/03/13/1-demo.png");
        presignVO.setExpireAt(1773379200000L);

        when(ossUploadService.createPresignedUpload(eq(loginUser), any())).thenReturn(presignVO);
    }

    @Test
    void anonymousUserCannotCreatePresignedUpload() throws Exception {
        mockMvc.perform(post("/oss/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"avatar\",\"fileName\":\"avatar.png\",\"contentType\":\"image/png\",\"fileSize\":1024}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void loginUserCanCreatePresignedUpload() throws Exception {
        mockMvc.perform(post("/oss/presign")
                        .sessionAttr(UserConstant.LOGIN_USER, loginUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"avatar\",\"fileName\":\"avatar.png\",\"contentType\":\"image/png\",\"fileSize\":1024}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.objectKey").value("avatars/2026/03/13/1-demo.png"))
                .andExpect(jsonPath("$.data.publicUrl").value("https://example-bucket.oss-cn-hangzhou.aliyuncs.com/avatars/2026/03/13/1-demo.png"));

        verify(ossUploadService).createPresignedUpload(eq(loginUser), any());
    }

    @Test
    void loginUserCanCreateBlogImagePresignedUpload() throws Exception {
        mockMvc.perform(post("/oss/presign")
                        .sessionAttr(UserConstant.LOGIN_USER, loginUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"blogImage\",\"fileName\":\"photo-1.png\",\"contentType\":\"image/png\",\"fileSize\":2048}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.method").value("PUT"));

        verify(ossUploadService).createPresignedUpload(eq(loginUser), any());
    }
}

