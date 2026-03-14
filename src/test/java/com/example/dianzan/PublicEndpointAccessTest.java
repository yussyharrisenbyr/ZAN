package com.example.dianzan;

import com.example.dianzan.config.CorsConfig;
import com.example.dianzan.controller.MainController;
import com.example.dianzan.controller.NotificationController;
import com.example.dianzan.controller.UserController;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.mapper.NotificationMapper;
import com.example.dianzan.mapper.UserMapper;
import com.example.dianzan.service.UserService;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        PublicEndpointAccessTest.TestApplication.class,
        MainController.class,
        NotificationController.class,
        UserController.class,
        CorsConfig.class,
        PublicEndpointAccessTest.MockConfig.class
})
class PublicEndpointAccessTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlogMapper blogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Cache<String, Object> localCache;

    @TestConfiguration
    static class MockConfig {

        @Bean
        BlogMapper blogMapper() {
            return mock(BlogMapper.class);
        }

        @Bean
        UserMapper userMapper() {
            return mock(UserMapper.class);
        }

        @Bean
        NotificationMapper notificationMapper() {
            return mock(NotificationMapper.class);
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

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }
    }

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(localCache.getIfPresent(anyString())).thenReturn(null);
        when(blogMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(blogMapper.selectTotalThumbCount()).thenReturn(0L);
        when(blogMapper.selectDistinctAuthorCount()).thenReturn(0L);
        when(notificationMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void anonymousUserCanAccessPublicEndpoints() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/shell-embed-guard.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/oss-upload.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/avatar-render.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/follow-list.html"))
                .andExpect(status().isOk());

        assertThat(mockMvc.perform(get("/favicon.ico"))
                .andReturn()
                .getResponse()
                .getStatus()).isNotEqualTo(401);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("ok"));

        mockMvc.perform(get("/site/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalBlogs").value(0))
                .andExpect(jsonPath("$.data.unreadNotifications").value(0));

        mockMvc.perform(get("/user/get/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/notification/unread/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void anonymousUserStillCannotAccessProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/notification/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void trailingSlashOnPublicEndpointShouldNotBeBlockedAsUnauthorized() throws Exception {
        int status = mockMvc.perform(get("/site/overview/"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotEqualTo(401);
    }

    @Test
    void optionsPreflightShouldNotBeBlockedByLoginInterceptor() throws Exception {
        int status = mockMvc.perform(options("/notification/list")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotEqualTo(401);
    }
}
