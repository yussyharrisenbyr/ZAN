package com.example.dianzan.model.vo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class FollowPageVO {
    private int page;
    private int size;
    private long total;
    private String keyword;
    private boolean mutualOnly;
    private List<FollowUserVO> list = Collections.emptyList();
}

