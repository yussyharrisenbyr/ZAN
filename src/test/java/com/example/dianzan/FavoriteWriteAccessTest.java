package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.controller.FavoriteController;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.FavoriteActionResponse;
import com.example.dianzan.service.FavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        FavoriteWriteAccessTest.TestApplication.class,
        FavoriteController.class,
        CorsConfig.class,
        FavoriteWriteAccessTest.MockConfig.class
})
class FavoriteWriteAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        FavoriteService favoriteService() {
            return mock(FavoriteService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FavoriteService favoriteService;

    private MockHttpSession loginSession;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setUsername("自己");
        user.setRole(0);
        loginSession = new MockHttpSession();
        loginSession.setAttribute(UserConstant.LOGIN_USER, user);

        FavoriteActionResponse doResp = new FavoriteActionResponse();
        doResp.setHasFavorite(true);
        doResp.setFavoriteCount(5L);
        when(favoriteService.doFavorite(any(), any())).thenReturn(doResp);

        FavoriteActionResponse undoResp = new FavoriteActionResponse();
        undoResp.setHasFavorite(false);
        undoResp.setFavoriteCount(4L);
        when(favoriteService.undoFavorite(any(), any())).thenReturn(undoResp);
    }

    @Test
    void anonymousUserCannotWriteFavoriteState() throws Exception {
        mockMvc.perform(post("/favorite/do")
                        .contentType("application/json")
                        .content("{\"blogId\":123}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void loggedInUserCanFavoriteAndUndoFavorite() throws Exception {
        mockMvc.perform(post("/favorite/do")
                        .session(loginSession)
                        .contentType("application/json")
                        .content("{\"blogId\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasFavorite").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(5));

        mockMvc.perform(post("/favorite/undo")
                        .session(loginSession)
                        .contentType("application/json")
                        .content("{\"blogId\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasFavorite").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(4));

        verify(favoriteService).doFavorite(any(), any());
        verify(favoriteService).undoFavorite(any(), any());
    }
}

