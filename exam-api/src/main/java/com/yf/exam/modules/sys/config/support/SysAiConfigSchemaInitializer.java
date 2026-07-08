package com.yf.exam.modules.sys.config.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时尝试创建 AI 配置表，避免未执行增量脚本时页面报错。
 */
@Component
@Order(50)
@Slf4j
public class SysAiConfigSchemaInitializer implements ApplicationRunner {

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS `sys_ai_config` ("
                    + " `id` varchar(32) NOT NULL COMMENT 'ID',"
                    + " `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用数据库配置',"
                    + " `provider` varchar(32) DEFAULT NULL COMMENT '提供方 openai/ragflow',"
                    + " `base_url` varchar(512) DEFAULT NULL COMMENT 'API地址',"
                    + " `api_key` varchar(512) DEFAULT NULL COMMENT 'API Key',"
                    + " `chat_id` varchar(128) DEFAULT NULL COMMENT 'RAGFlow聊天助手ID',"
                    + " `model_name` varchar(128) DEFAULT NULL COMMENT '模型名称',"
                    + " `timeout_seconds` int DEFAULT NULL COMMENT '请求超时秒数',"
                    + " `ragflow_base_url` varchar(512) DEFAULT NULL COMMENT 'RAGFlow知识库API地址',"
                    + " `ragflow_api_key` varchar(512) DEFAULT NULL COMMENT 'RAGFlow API Key',"
                    + " `ragflow_dataset_id` varchar(128) DEFAULT NULL COMMENT 'RAGFlow知识库ID',"
                    + " `ragflow_dataset_name` varchar(255) DEFAULT NULL COMMENT 'RAGFlow知识库名称',"
                    + " `ragflow_auto_upload` tinyint(1) DEFAULT NULL COMMENT '上传试卷时同步写入RAGFlow',"
                    + " `ragflow_upload_fail_fast` tinyint(1) DEFAULT NULL COMMENT 'RAGFlow上传失败时中断导入',"
                    + " `create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                    + " `update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                    + " PRIMARY KEY (`id`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI接口配置'";

    private static final String INSERT_DEFAULT_SQL =
            "INSERT INTO `sys_ai_config` (`id`, `enabled`, `create_time`, `update_time`) "
                    + "SELECT '1', 1, NOW(), NOW() FROM DUAL "
                    + "WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_config` WHERE `id` = '1')";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            jdbcTemplate.execute(INSERT_DEFAULT_SQL);
            log.info("sys_ai_config 表已就绪");
        } catch (Exception e) {
            log.warn("自动创建 sys_ai_config 表失败，请手动执行 docs/安装资源/sys_ai_config.sql：{}", e.getMessage());
        }
    }
}
