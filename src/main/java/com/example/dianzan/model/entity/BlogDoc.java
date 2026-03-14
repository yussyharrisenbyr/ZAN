package com.example.dianzan.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "blog", writeTypeHint = WriteTypeHint.FALSE)
public class BlogDoc {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword)
    private String coverImg;

    @Field(type = FieldType.Integer)
    private Integer thumbCount;

    @Field(type = FieldType.Long)
    private Long userId;
}
