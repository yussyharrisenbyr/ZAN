package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.controller.BlogController;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.repository.BlogEsRepository;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.ThumbService;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

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
        UserBlogPublicAccessTest.TestApplication.class,
        BlogController.class,
        CorsConfig.class,
        UserBlogPublicAccessTest.MockConfig.class
})
class UserBlogPublicAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        BlogService blogService() {
            return mock(BlogService.class);
        }

        @Bean
        BlogEsRepository blogEsRepository() {
            return mock(BlogEsRepository.class);
        }

        @Bean
        ThumbService thumbService() {
            return mock(ThumbService.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate() {
            return (RedisTemplate<String, Object>) mock(RedisTemplate.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        Cache<String, Object> localCache() {
            return (Cache<String, Object>) mock(Cache.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlogService blogService;

    @BeforeEach
    void setUp() {
        BlogVO blog = new BlogVO();
        blog.setId(101L);
        blog.setUserId(2L);
        blog.setTitle("Java 分页实践");
        blog.setContent("分页查询比一次性全量拉取更稳。");
        blog.setCoverImg("https://static.example.com/blogs/blog-101-cover.png");
        blog.setImageList(List.of(
                "https://static.example.com/blogs/blog-101-1.png",
                "https://static.example.com/blogs/blog-101-2.png"
        ));
        blog.setThumbCount(18);
        blog.setAuthorName("测试作者");
        blog.setAuthorAvatarUrl("https://static.example.com/authors/blog-2.png");

        BlogPageVO page = new BlogPageVO();
        page.setSize(15);
        page.setTotal(1L);
        page.setSort("thumb");
        page.setKeyword("java");
        page.setHasMore(false);
        page.setNextCursor("");
        page.setList(List.of(blog));

        when(blogService.listUserBlogs(eq(2L), eq("thumb:18:120"), eq(15), eq("thumb"), eq("java"), any(HttpServletRequest.class)))
                .thenReturn(page);
    }

    @Test
    void anonymousUserCanAccessPublicUserBlogPageWithPagination() throws Exception {
        mockMvc.perform(get("/blog/user")
                        .param("userId", "2")
                        .param("cursor", "thumb:18:120")
                        .param("size", "15")
                        .param("sort", "thumb")
                        .param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.sort").value("thumb"))
                .andExpect(jsonPath("$.data.keyword").value("java"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(""))
                .andExpect(jsonPath("$.data.list[0].id").value(101))
                .andExpect(jsonPath("$.data.list[0].authorName").value("测试作者"))
                .andExpect(jsonPath("$.data.list[0].authorAvatarUrl").value("https://static.example.com/authors/blog-2.png"))
                .andExpect(jsonPath("$.data.list[0].coverImg").value("https://static.example.com/blogs/blog-101-cover.png"))
                .andExpect(jsonPath("$.data.list[0].imageList[0]").value("https://static.example.com/blogs/blog-101-1.png"))
                .andExpect(jsonPath("$.data.list[0].imageList[1]").value("https://static.example.com/blogs/blog-101-2.png"));

        verify(blogService).listUserBlogs(eq(2L), eq("thumb:18:120"), eq(15), eq("thumb"), eq("java"), any(HttpServletRequest.class));
    }

    @Test
    void anonymousUserStillCannotOpenOwnBlogPageWithoutLogin() throws Exception {
        mockMvc.perform(get("/blog/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}


