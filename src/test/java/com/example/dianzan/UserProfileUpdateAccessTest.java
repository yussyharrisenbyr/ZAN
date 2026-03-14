package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.controller.UserController;
import com.example.dianzan.model.dto.UserProfileUpdateRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.UserProfileVO;
import com.example.dianzan.service.UserService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        UserProfileUpdateAccessTest.TestApplication.class,
        UserController.class,
        CorsConfig.class,
        UserProfileUpdateAccessTest.MockConfig.class
})
class UserProfileUpdateAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserService userService() {
            return mock(UserService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private User loginUser;
    private UserProfileVO updatedProfile;

    @BeforeEach
    void setUp() {
        loginUser = new User();
        loginUser.setId(1L);
        loginUser.setUserAccount("13800000001");
        loginUser.setUsername("自己");
        loginUser.setRole(0);

        updatedProfile = new UserProfileVO();
        updatedProfile.setUserId(1L);
        updatedProfile.setUsername("自己");
        updatedProfile.setSelf(true);
        updatedProfile.setAge(26);
        updatedProfile.setAvatarUrl("https://static.example.com/avatar-1.png");
        updatedProfile.setBio("喜欢记录技术与生活。\n欢迎交流。");

        when(userService.getLoginUser(any(HttpServletRequest.class))).thenReturn(loginUser);
        when(userService.getProfile(eq(1L), any(HttpServletRequest.class))).thenReturn(updatedProfile);
    }

    @Test
    void anonymousUserCannotUpdateProfile() throws Exception {
        mockMvc.perform(post("/user/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":26,\"bio\":\"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void loginUserCanUpdateOwnAgeAndBio() throws Exception {
        mockMvc.perform(post("/user/me/profile")
                        .sessionAttr(UserConstant.LOGIN_USER, loginUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":26,\"bio\":\"喜欢记录技术与生活。\\n欢迎交流。\",\"avatarUrl\":\"https://static.example.com/avatar-1.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.age").value(26))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://static.example.com/avatar-1.png"))
                .andExpect(jsonPath("$.data.bio").value("喜欢记录技术与生活。\n欢迎交流。"));

        verify(userService).updateMyProfile(eq(loginUser), any(UserProfileUpdateRequest.class));
    }
}

