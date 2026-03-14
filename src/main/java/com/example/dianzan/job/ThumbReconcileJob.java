package com.example.dianzan.job;

import cn.hutool.json.JSONUtil;
import com.example.dianzan.constant.ThumbConstant;
import com.example.dianzan.listener.thumb.msg.ThumbEvent;
import com.example.dianzan.model.dto.thumb.ThumbRedisData;
import com.example.dianzan.model.entity.Thumb;
import com.example.dianzan.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 点赞终极对账与补偿任务 (包含水位线防误判机制)
 *
 * @author pine
 */
@Slf4j
@Component
public class ThumbReconcileJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    /**
     * 定时任务入口（每天凌晨2点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        long startTime = System.currentTimeMillis();
        log.info("开始执行凌晨点赞数据全量对账与补偿任务...");

        // 【核心防线】：计算安全时间水位线 Watermark (往前推 5 分钟，即 1:55:00)
        // 规避掉还在 MQ 管道里排队飞驰的“在途数据”
        long safeWatermark = startTime - (5 * 60 * 1000);

        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while (cursor.hasNext()) {
                String redisKey = cursor.next();
                Long userId = Long.valueOf(redisKey.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));

                // 1. 获取 Redis 中的真实有效记录（过滤掉水位线之后的在途数据）
                Set<Long> validRedisBlogIds = getValidRedisBlogIds(redisKey, safeWatermark, userId);

                if (validRedisBlogIds.isEmpty()) {
                    continue;
                }

                // 2. 高效查询 MySQL 数据（只查被过滤后存在于 validRedisBlogIds 里的数据，避免全表扫描）
                // 内存优化：只查 BlogId 这一个字段，绝对不用 .list() 拉取全表实体
                List<Object> mysqlBlogIdObjs = thumbService.listObjs(
                        thumbService.lambdaQuery()
                                .select(Thumb::getBlogId)
                                .eq(Thumb::getUserId, userId)
                                .in(Thumb::getBlogId, validRedisBlogIds)
                                .getWrapper()
                );

                Set<Long> mysqlBlogIds = mysqlBlogIdObjs.stream()
                        .map(obj -> Long.valueOf(obj.toString()))
                        .collect(Collectors.toSet());

                // 3. 对账核心：计算差异（Redis 里有，但 MySQL 里确认没有的漏网之鱼）
                validRedisBlogIds.removeAll(mysqlBlogIds);

                // 4. 执行补偿
                if (!validRedisBlogIds.isEmpty()) {
                    log.warn("对账发现数据缺失！开始补偿 userId={} 的漏网博客: {}", userId, validRedisBlogIds);
                    sendCompensationEvents(userId, validRedisBlogIds);
                }
            }
        } catch (Exception e) {
            log.error("对账补偿任务执行异常", e);
        }

        log.info("对账补偿任务完成，总耗时 {}ms", System.currentTimeMillis() - startTime);
    }

    /**
     * 获取过滤掉“在途数据”后的 Redis 博客ID集合
     */
    private Set<Long> getValidRedisBlogIds(String redisKey, long safeWatermark, Long userId) {
        Set<Long> validBlogIds = new HashSet<>();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long blogId = Long.valueOf(entry.getKey().toString());
            String jsonVal = (String) entry.getValue();

            try {
                ThumbRedisData redisData = JSONUtil.toBean(jsonVal, ThumbRedisData.class);
                // 判断：如果操作时间 < 水位线（也就是非常安全的历史数据），才加入对账列表
                if (redisData.getActionTime() != null && redisData.getActionTime() < safeWatermark) {
                    validBlogIds.add(blogId);
                } else {
                    log.debug("跳过在途数据: userId={}, blogId={}", userId, blogId);
                }
            } catch (Exception e) {
                // 兼容旧的非 JSON 格式数据，或发生序列化错误时，保险起见加入对账列表
                validBlogIds.add(blogId);
            }
        }
        return validBlogIds;
    }

    /**
     * 发送补偿事件到Pulsar
     */
    private void sendCompensationEvents(Long userId, Set<Long> blogIds) {
        blogIds.forEach(blogId -> {
            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .userId(userId)
                    .blogId(blogId)
                    .type(ThumbEvent.EventType.INCR)
                    .eventTime(LocalDateTime.now())
                    .build();

            pulsarTemplate.sendAsync("thumb-topic", thumbEvent)
                    .exceptionally(ex -> {
                        log.error("补偿事件发送 MQ 失败: userId={}, blogId={}", userId, blogId, ex);
                        return null;
                    });
        });
    }
}