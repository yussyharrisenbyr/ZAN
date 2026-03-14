package com.example.dianzan.listener.thumb;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dianzan.listener.thumb.msg.ThumbEvent;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.mapper.ThumbMapper;
import com.example.dianzan.model.entity.Thumb;
import com.example.dianzan.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbConsumer {

    private static final String BLOG_LIST_CACHE_KEY = "blog:list";
    private static final String DEDUP_KEY_PREFIX = "thumb:consumer:msg:";

    private final BlogMapper blogMapper;
    private final ThumbService thumbService;
    private final RedisTemplate<String, Object> redisTemplate;
    @Resource
    private ThumbMapper thumbMapper;
    @PulsarListener(topics = "thumb-dlq-topic")
    public void consumerDlq(Message<ThumbEvent> message) {
        MessageId messageId = message.getMessageId();
        log.info("dlq message = {}", messageId);
        log.info("消息 {} 已入库", messageId);
        log.info("已通知相关人员 {} 处理消息 {}", "坤哥", messageId);
    }

    // 批量处理配置
    @PulsarListener(
            subscriptionName = "thumb-subscription",
            topics = "thumb-topic",
            schemaType = SchemaType.JSON,
            batch = true,
            subscriptionType = SubscriptionType.Shared,
            // consumerCustomizer = "thumbConsumerConfig",
            // 引用 NACK 重试策略
            negativeAckRedeliveryBackoff = "negativeAckRedeliveryBackoff",
            // 引用 ACK 超时重试策略
            ackTimeoutRedeliveryBackoff = "ackTimeoutRedeliveryBackoff",
            // 引用死信队列策略
            deadLetterPolicy = "deadLetterPolicy"
    )
    @Transactional(rollbackFor = Exception.class)
    public void processBatch(List<Message<ThumbEvent>> messages) {
        log.info("ThumbConsumer processBatch: {}", messages.size());
        if (messages.isEmpty()) {
            return;
        }
//         for (Message<ThumbEvent> message : messages) {
//             log.info("message.getMessageId() = {}", message.getMessageId());
//         }
//         if (true) {
//             throw new RuntimeException("ThumbConsumer processBatch failed");
//         }
        List<Thumb> thumbsToInsert = new ArrayList<>();

        // 并行处理消息

        // 提取事件并过滤无效消息（单条 try-catch，避免整批回滚）
        List<ThumbEvent> events = new ArrayList<>();
        for (Message<ThumbEvent> msg : messages) {
            try {
                ThumbEvent e = msg.getValue();
                if (e == null || e.getUserId() == null || e.getBlogId() == null || e.getType() == null) {
                    log.warn("跳过无效点赞消息: messageId={} event={}", msg.getMessageId(), e);
                    continue;
                }
                String dedupKey = DEDUP_KEY_PREFIX + msg.getMessageId();
                Boolean firstSeen = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofMinutes(10));
                if (Boolean.FALSE.equals(firstSeen)) {
                    log.info("跳过重复消费的点赞消息: messageId={} userId={} blogId={} type={}", msg.getMessageId(), e.getUserId(), e.getBlogId(), e.getType());
                    continue;
                }
                events.add(e);
            } catch (Exception ex) {
                log.warn("解析点赞消息失败，已跳过: messageId={}", msg.getMessageId(), ex);
            }
        }


        // 按(userId, blogId)分组，并获取每个分组的最新事件
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = events.stream()
                .collect(Collectors.groupingBy(
                        e -> Pair.of(e.getUserId(), e.getBlogId()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    // 按时间升序排序，取最后一个作为最新事件
                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime,
                                            Comparator.nullsLast(Comparator.naturalOrder())));
                                    if (list.size() % 2 == 0) {
                                        return null;
                                    }
                                    return list.get(list.size() - 1);
                                }
                        )
                ));

        latestEvents.forEach((userBlogPair, event) -> {
            if (event == null) {
                return;
            }
            ThumbEvent.EventType finalAction = event.getType();

            if (finalAction == ThumbEvent.EventType.INCR) {
                // 只有实际需要新增时才计数，避免重复投递导致重复加一
                boolean exists = thumbMapper.selectCount(new LambdaQueryWrapper<Thumb>()
                        .eq(Thumb::getUserId, event.getUserId())
                        .eq(Thumb::getBlogId, event.getBlogId())) > 0;
                if (!exists) {
                    Thumb thumb = new Thumb();
                    thumb.setBlogId(event.getBlogId());
                    thumb.setUserId(event.getUserId());
                    thumbsToInsert.add(thumb);
                }
            } else {
                // 只有真实删除到数据时才扣减计数
                int deleted = thumbMapper.delete(new LambdaQueryWrapper<Thumb>()
                        .eq(Thumb::getUserId, event.getUserId())
                        .eq(Thumb::getBlogId, event.getBlogId()));
                // 同步场景下，删除受影响为 0 则认为已是最终态，无需再处理计数
            }
        });

        batchInsertThumbs(thumbsToInsert);
        invalidateBlogListCache();
    }


    public void batchInsertThumbs(List<Thumb> thumbs) {
        if (!thumbs.isEmpty()) {
            // 直接调用我们手写的高级 SQL 方法，彻底解决批量异常连坐回滚问题！
            thumbMapper.insertIgnoreBatch(thumbs);
        }
    }

    private void invalidateBlogListCache() {
        try {
            Set<String> keys = redisTemplate.keys(BLOG_LIST_CACHE_KEY + ":*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // 缓存失效失败不应影响点赞主流程，避免触发 NACK/DLQ
            log.warn("点赞后清理首页缓存失败，跳过本次失效", e);
        }
    }
}