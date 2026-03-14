package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.controller.ThumbController;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.service.ThumbService;
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
        UserThumbPublicAccessTest.TestApplication.class,
        ThumbController.class,
        CorsConfig.class,
        UserThumbPublicAccessTest.MockConfig.class
})
class UserThumbPublicAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        ThumbService thumbService() {
            return mock(ThumbService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ThumbService thumbService;

    @BeforeEach
    void setUp() {
        BlogVO blog = new BlogVO();
        blog.setId(201L);
        blog.setUserId(3L);
        blog.setTitle("被点赞的博客");
        blog.setContent("这是点赞记录里的文章。 ");
        blog.setThumbCount(6);
        blog.setAuthorName("原作者");
        blog.setAuthorAvatarUrl("https://static.example.com/authors/thumb-3.png");
        blog.setThumbTime(new Date(1_762_000_000_000L));

        BlogPageVO page = new BlogPageVO();
        page.setSize(15);
        page.setTotal(1L);
        page.setSort("liked");
        page.setKeyword("");
        page.setHasMore(false);
        page.setNextCursor("");
        page.setList(List.of(blog));

        when(thumbService.listUserThumbBlogs(eq(2L), eq("liked:1762000000000:99"), eq(15), any(HttpServletRequest.class)))
                .thenReturn(page);
    }

    @Test
    void anonymousUserCanAccessPublicThumbHistoryWithTargetUserId() throws Exception {
        mockMvc.perform(get("/thumb/user")
                        .param("userId", "2")
                        .param("cursor", "liked:1762000000000:99")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.sort").value("liked"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(""))
                .andExpect(jsonPath("$.data.list[0].id").value(201))
                .andExpect(jsonPath("$.data.list[0].authorName").value("原作者"))
                .andExpect(jsonPath("$.data.list[0].authorAvatarUrl").value("https://static.example.com/authors/thumb-3.png"));

        verify(thumbService).listUserThumbBlogs(eq(2L), eq("liked:1762000000000:99"), eq(15), any(HttpServletRequest.class));
    }

    @Test
    void anonymousUserStillCannotOpenOwnThumbHistoryWithoutLogin() throws Exception {
        mockMvc.perform(get("/thumb/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}

