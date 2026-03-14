package com.example.dianzan.repository;

import com.example.dianzan.model.entity.BlogDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface BlogEsRepository extends ElasticsearchRepository<BlogDoc, Long> {
}
