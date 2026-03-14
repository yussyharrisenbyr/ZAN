package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.controller.BlogController;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.HotBlogVO;
import com.example.dianzan.repository.BlogEsRepository;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.ThumbService;
import com.github.benmanes.caffeine.cache.Cache;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        HotBlogAccessTest.TestApplication.class,
        BlogController.class,
        CorsConfig.class,
        HotBlogAccessTest.MockConfig.class
})
class HotBlogAccessTest {

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

    @Autowired
    private ThumbService thumbService;

    private User loginUser;

    @BeforeEach
    void setUp() {
        loginUser = new User();
        loginUser.setId(1L);
        loginUser.setUserAccount("13800000001");
        loginUser.setUsername("测试用户");

        HotBlogVO hotBlog = new HotBlogVO();
        hotBlog.setBlogId(101L);
        hotBlog.setTitle("本周最热文章");
        hotBlog.setCoverImg("https://static.example.com/hot-101.png");
        hotBlog.setUserId(2L);
        hotBlog.setAuthorName("热门作者");
        hotBlog.setAuthorAvatarUrl("https://static.example.com/authors/hot-2.png");
        hotBlog.setThumbCount(39);
        hotBlog.setWeeklyThumbCount(12L);

        when(blogService.listWeeklyHotBlogs(5)).thenReturn(List.of(hotBlog));
        when(thumbService.hasThumb(101L, 1L)).thenReturn(true);
    }

    @Test
    void anonymousUserGetsHotBlogsWithGreyHeartState() throws Exception {
        mockMvc.perform(get("/blog/hot").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].blogId").value(101))
                .andExpect(jsonPath("$.data[0].hasThumb").value(false));
    }

    @Test
    void loginUserGetsHotBlogsWithLikeState() throws Exception {
        mockMvc.perform(get("/blog/hot")
                        .param("limit", "5")
                        .sessionAttr(UserConstant.LOGIN_USER, loginUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].blogId").value(101))
                .andExpect(jsonPath("$.data[0].hasThumb").value(true));

        verify(thumbService).hasThumb(eq(101L), eq(1L));
    }
}

