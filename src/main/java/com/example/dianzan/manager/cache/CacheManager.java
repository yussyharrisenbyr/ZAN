package com.example.dianzan.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.model.entity.Blog;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存与防御大闸（包含本地微缓存、TopK热点探测、布隆过滤器、防击穿DCL）
 *
 * @author pine
 */
@Component
@Slf4j
public class CacheManager {

    private TopK hotKeyDetector;

    private Cache<String, Object> localCache;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private BlogMapper blogMapper;

    // 定义布隆过滤器 (专门存储真实存在的 BlogId)
    private BloomFilter<Long> blogBloomFilter;

    @PostConstruct
    public void initBloomFilter() {
        log.info("开始初始化布隆过滤器...");
        blogBloomFilter = BloomFilter.create(Funnels.longFunnel(), 100000, 0.01);
        List<Object> blogIds = blogMapper.selectObjs(new QueryWrapper<Blog>().select("id"));
        if (blogIds != null && !blogIds.isEmpty()) {
            for (Object blogId : blogIds) {
                if (blogId == null) {
                    continue;
                }
                if (blogId instanceof Number number) {
                    blogBloomFilter.put(number.longValue());
                } else {
                    try {
                        blogBloomFilter.put(Long.parseLong(String.valueOf(blogId)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        log.info("布隆过滤器初始化完成，共加载 {} 条真实博客数据", blogIds == null ? 0 : blogIds.stream().filter(Objects::nonNull).count());
    }

    public boolean mightContainBlog(Long blogId) {
        if (blogBloomFilter == null) return true;
        return blogBloomFilter.mightContain(blogId);
    }

    public void putBlog(Long blogId) {
        if (blogBloomFilter != null) {
            blogBloomFilter.put(blogId);
        }
    }

    @Bean
    public TopK getHotKeyDetector() {
        hotKeyDetector = new HeavyKeeper(
                // 监控 Top 100 Key
                100,
                100000,
                5,
                0.92,
                10
        );
        return hotKeyDetector;
    }

    @Bean
    public Cache<String, Object> localCache() {
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                // 提高本地缓存命中率，减少应用重启后和常规浏览过程中的频繁回源
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }

    /**
     * 高并发防击穿获取缓存数据的核心方法
     */
    public Object get(String hashKey, String key) {
        String compositeKey = buildCacheKey(hashKey, key);

        // 【第一重检查】：99.99% 的请求在这里就被本地缓存瞬间挡回去了，极速返回
        Object value = localCache.getIfPresent(compositeKey);
        if (value != null) {
            log.debug("本地缓存获取到数据 {} = {}", compositeKey, value);
            hotKeyDetector.add(key, 1);
            return value;
        }

        // 【核心优化 2】：双重检查锁 (DCL) + intern() 单机并发防线
        // 当本地缓存没有时，如果有 30 万个请求同时到达，保证只有 1 个请求能去查 Redis
        synchronized (compositeKey.intern()) {

            // 【第二重检查】：抢到锁之后，看一眼是不是已经被前一个天选之子查好放进缓存了
            value = localCache.getIfPresent(compositeKey);
            if (value != null) {
                // 剩下的 299,999 个线程会在这里直接拿到数据返回！
                return value;
            }

            // 【真正的天选之子】：只有第 1 个拿到锁的线程会走到这里，去查 Redis
            Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
            if (redisValue == null) {
                return null;
            }

            // 记录访问并判断是否为 Hot Key
            AddResult addResult = hotKeyDetector.add(key, 1);

            // 如果是热 Key，塞入本地缓存（让后面的 299,999 个线程醒来后能直接拿到）
            if (addResult.isHotKey()) {
                localCache.put(compositeKey, redisValue);
            }

            return redisValue;
        }
    }

    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = buildCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        if (object == null) {
            return;
        }
        // 如果传入 null，说明业务要求删除该缓存（例如取消点赞时）
        if (value == null) {
            localCache.invalidate(compositeKey);
        } else {
            localCache.put(compositeKey, value);
        }
    }

    // 定时清理过期的热 Key 检测数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        if (hotKeyDetector != null) {
            hotKeyDetector.fading();
        }
    }
}