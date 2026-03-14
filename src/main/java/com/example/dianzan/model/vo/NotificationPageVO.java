package com.example.dianzan.model.vo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class NotificationPageVO {
    private int page;
    private int size;
    private long total;
    private List<NotificationItemVO> list = Collections.emptyList();
}

