package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.controller.FeedbackController;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        FeedbackSubmitAccessTest.TestApplication.class,
        FeedbackController.class,
        CorsConfig.class,
        FeedbackSubmitAccessTest.MockConfig.class
})
class FeedbackSubmitAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        FeedbackService feedbackService() {
            return mock(FeedbackService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedbackService feedbackService;

    private User loginUser;

    @BeforeEach
    void setUp() {
        reset(feedbackService);
        loginUser = new User();
        loginUser.setId(1L);
        loginUser.setUsername("建议用户");
        loginUser.setRole(0);
        when(feedbackService.submitFeedback(any(), any(HttpServletRequest.class))).thenReturn(1001L);
    }

    @Test
    void anonymousUserCanSubmitFeedback() throws Exception {
        mockMvc.perform(post("/feedback/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"首页可以再加一个深色模式开关\",\"contact\":\"13800000000\",\"pagePath\":\"/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1001));

        verify(feedbackService).submitFeedback(any(), any(HttpServletRequest.class));
    }

    @Test
    void loggedInUserCanAlsoSubmitFeedback() throws Exception {
        mockMvc.perform(post("/feedback/submit")
                        .sessionAttr(UserConstant.LOGIN_USER, loginUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"建议支持在首页筛选视频类内容\",\"contact\":\"wechat:demo-user\",\"pagePath\":\"/?q=test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1001));

        verify(feedbackService).submitFeedback(any(), any(HttpServletRequest.class));
    }
}

