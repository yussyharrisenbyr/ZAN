package com.example.dianzan.model.vo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class BlogPageVO {
    private int size;
    private long total;
    private String sort = "latest";
    private String keyword = "";
    private boolean hasMore;
    private String nextCursor = "";
    private List<BlogVO> list = Collections.emptyList();
}


