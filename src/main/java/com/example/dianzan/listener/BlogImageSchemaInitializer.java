package com.example.dianzan.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BlogImageSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public BlogImageSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE blog ADD COLUMN IF NOT EXISTS imageUrls TEXT NULL COMMENT '博客图片 JSON 数组' AFTER coverImg");
            log.info("博客多图字段初始化完成：blog.imageUrls");
        } catch (Exception e) {
            log.warn("自动初始化 blog.imageUrls 失败，请确认数据库权限或手动执行 sql/create.sql 中的建表语句", e);
        }
    }
}
