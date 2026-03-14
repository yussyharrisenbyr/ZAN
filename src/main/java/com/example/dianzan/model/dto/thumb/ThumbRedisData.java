package com.example.dianzan.model.dto.thumb;

import lombok.Data;
import java.util.Date;

@Data
public class ThumbRedisData {
    /**
     * 点赞记录的 ID (对应数据库 thumb 表的主键)
     */
    private Long thumbId;

    /**
     * 过期时间 (通常是博客创建时间 + 30天)
     */
    private Date expireTime;
    // 【新增】：操作时间（记录真实点赞发生的绝对毫秒级时间戳，用于对账防误判）
    private Long actionTime;
}