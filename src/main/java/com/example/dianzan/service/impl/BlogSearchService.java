package com.example.dianzan.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.entity.BlogDoc;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.service.BlogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlogSearchService {

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private BlogService blogService;

    public List<BlogVO> search(String keyword, HttpServletRequest request) throws IOException {
        SearchResponse<BlogDoc> response = esClient.search(s -> s
                .index("blog")
                .query(q -> q.multiMatch(m -> m
                        .fields("title", "content")
                        .query(keyword)))
                  .highlight(h -> h
                          .fields("title", HighlightField.of(f -> f))
                          .fields("content", HighlightField.of(f -> f))
                          .preTags("<em class=\"hl\">")
                          .postTags("</em>")),
                BlogDoc.class);

        Map<Long, BlogDoc> resultMap = new LinkedHashMap<>();
        for (Hit<BlogDoc> hit : response.hits().hits()) {
            BlogDoc doc = hit.source();
            if (doc == null) continue;
            Map<String, List<String>> hl = hit.highlight();
            if (hl.containsKey("title")) doc.setTitle(String.join("", hl.get("title")));
            if (hl.containsKey("content")) doc.setContent(String.join("", hl.get("content")));
            resultMap.putIfAbsent(doc.getId(), doc);
        }

        if (resultMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderedIds = new ArrayList<>(resultMap.keySet());
        Map<Long, Blog> blogMap = blogService.listByIds(orderedIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Blog::getId, Function.identity(), (left, right) -> left));

        List<Blog> orderedBlogs = orderedIds.stream()
                .map(blogMap::get)
                .filter(Objects::nonNull)
                .toList();
        if (orderedBlogs.isEmpty()) {
            return Collections.emptyList();
        }

        List<BlogVO> blogVOList = blogService.getBlogVOList(orderedBlogs, request);
        blogVOList.forEach(blogVO -> {
            BlogDoc doc = resultMap.get(blogVO.getId());
            if (doc == null) {
                return;
            }
            if (doc.getTitle() != null) {
                blogVO.setTitle(doc.getTitle());
            }
            if (doc.getContent() != null) {
                blogVO.setContent(doc.getContent());
            }
        });
        return blogVOList;
    }
}
