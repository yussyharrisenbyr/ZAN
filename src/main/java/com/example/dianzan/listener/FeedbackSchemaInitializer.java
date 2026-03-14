package com.example.dianzan.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FeedbackSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!tableExists("feedback")) {
                jdbcTemplate.execute("""
                        CREATE TABLE feedback (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            userId BIGINT NULL COMMENT '提交反馈的用户，匿名可为空',
                            usernameSnapshot VARCHAR(128) NULL COMMENT '提交时的用户名快照',
                            contact VARCHAR(100) NULL COMMENT '联系方式（可选）',
                            content VARCHAR(500) NOT NULL COMMENT '反馈内容',
                            pagePath VARCHAR(255) NULL COMMENT '反馈来源页面',
                            status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理',
                            createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
                        ) COMMENT='用户反馈表'
                        """);
            }
            ensureIndex("feedback", "idx_feedback_userId", "CREATE INDEX idx_feedback_userId ON feedback (userId)");
            ensureIndex("feedback", "idx_feedback_status", "CREATE INDEX idx_feedback_status ON feedback (status)");
            log.info("用户反馈表初始化完成：feedback");
        } catch (Exception e) {
            log.warn("自动初始化 feedback 表失败，请确认数据库权限或手动执行 sql/create.sql 中的建表语句", e);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void ensureIndex(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}

