package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.controller.FollowController;
import com.example.dianzan.controller.UserController;
import com.example.dianzan.model.vo.FollowPageVO;
import com.example.dianzan.model.vo.FollowUserVO;
import com.example.dianzan.model.vo.UserProfileVO;
import com.example.dianzan.service.FollowService;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        ProfileFollowPublicAccessTest.TestApplication.class,
        UserController.class,
        FollowController.class,
        CorsConfig.class,
        ProfileFollowPublicAccessTest.MockConfig.class
})
class ProfileFollowPublicAccessTest {

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

        @Bean
        FollowService followService() {
            return mock(FollowService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private FollowService followService;

    @BeforeEach
    void setUp() {
        UserProfileVO profile = new UserProfileVO();
        profile.setUserId(2L);
        profile.setUsername("测试用户");
        profile.setSelf(false);
        profile.setFollowing(false);
        profile.setAvatarUrl("https://static.example.com/profiles/user-2.png");
        FollowUserVO followUser = new FollowUserVO();
        followUser.setUserId(3L);
        followUser.setUsername("粉丝A");
        followUser.setUserAccount("13900000000");
        followUser.setAvatarUrl("https://static.example.com/profiles/user-3.png");
        followUser.setFollowing(false);
        followUser.setFollowingViewer(false);
        followUser.setMutualFollow(false);
        FollowPageVO followPage = new FollowPageVO();
        followPage.setPage(1);
        followPage.setSize(10);
        followPage.setTotal(1L);
        followPage.setList(List.of(followUser));

        FollowPageVO filteredFansPage = new FollowPageVO();
        filteredFansPage.setPage(2);
        filteredFansPage.setSize(5);
        filteredFansPage.setTotal(1L);
        filteredFansPage.setKeyword("粉");
        filteredFansPage.setMutualOnly(true);
        filteredFansPage.setList(List.of(followUser));

        when(userService.getProfile(eq(2L), any(HttpServletRequest.class))).thenReturn(profile);
        when(userService.getLoginUser(any(HttpServletRequest.class))).thenReturn(null);
        when(followService.listFollowers(eq(2L), eq(null), eq(1), eq(10), eq(null), eq(false))).thenReturn(followPage);
        when(followService.listFollowers(eq(2L), eq(null), eq(2), eq(5), eq("粉"), eq(true))).thenReturn(filteredFansPage);
        when(followService.listFollowees(eq(2L), eq(null), eq(1), eq(10), eq(null), eq(false))).thenReturn(followPage);
    }

    @Test
    void anonymousUserCanAccessPublicProfileAndFollowReadEndpoints() throws Exception {
        mockMvc.perform(get("/follow-list.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/user/profile").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://static.example.com/profiles/user-2.png"));

        mockMvc.perform(get("/follow/isFollow").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/follow/fans").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].userId").value(3))
                .andExpect(jsonPath("$.data.list[0].mutualFollow").value(false))
                .andExpect(jsonPath("$.data.list[0].avatarUrl").value("https://static.example.com/profiles/user-3.png"));

        mockMvc.perform(get("/follow/following").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.list[0].followingViewer").value(false));

        mockMvc.perform(get("/follow/fans")
                        .param("userId", "2")
                        .param("page", "2")
                        .param("size", "5")
                        .param("keyword", "粉")
                        .param("mutualOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.keyword").value("粉"))
                .andExpect(jsonPath("$.data.mutualOnly").value(true));

        verify(followService).listFollowers(2L, null, 2, 5, "粉", true);
    }

    @Test
    void anonymousUserStillCannotWriteFollowState() throws Exception {
        mockMvc.perform(post("/follow/do").param("userId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}

