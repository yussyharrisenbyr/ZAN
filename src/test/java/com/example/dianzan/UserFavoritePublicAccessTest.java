package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.controller.FavoriteController;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.service.FavoriteService;
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

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        UserFavoritePublicAccessTest.TestApplication.class,
        FavoriteController.class,
        CorsConfig.class,
        UserFavoritePublicAccessTest.MockConfig.class
})
class UserFavoritePublicAccessTest {

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

    @BeforeEach
    void setUp() {
        BlogVO blog = new BlogVO();
        blog.setId(301L);
        blog.setUserId(8L);
        blog.setTitle("被收藏的博客");
        blog.setContent("这是收藏列表里的文章。");
        blog.setThumbCount(12);
        blog.setAuthorName("收藏作者");
        blog.setFavoriteTime(new Date(1_762_100_000_000L));

        BlogPageVO page = new BlogPageVO();
        page.setSize(15);
        page.setTotal(1L);
        page.setSort("favorite");
        page.setKeyword("");
        page.setHasMore(false);
        page.setNextCursor("");
        page.setList(List.of(blog));

        when(favoriteService.listUserFavoriteBlogs(eq(2L), eq("favorite:1762100000000:88"), eq(15), any(HttpServletRequest.class)))
                .thenReturn(page);
    }

    @Test
    void anonymousUserCanAccessPublicFavoriteHistoryWithTargetUserId() throws Exception {
        mockMvc.perform(get("/favorite/user")
                        .param("userId", "2")
                        .param("cursor", "favorite:1762100000000:88")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.sort").value("favorite"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(""))
                .andExpect(jsonPath("$.data.list[0].id").value(301))
                .andExpect(jsonPath("$.data.list[0].authorName").value("收藏作者"));

        verify(favoriteService).listUserFavoriteBlogs(eq(2L), eq("favorite:1762100000000:88"), eq(15), any(HttpServletRequest.class));
    }

    @Test
    void anonymousUserStillCannotOpenOwnFavoriteHistoryWithoutLogin() throws Exception {
        mockMvc.perform(get("/favorite/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}

