-- AI 配置表（已有数据库执行本脚本）
CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用数据库配置',
  `provider` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '提供方 openai/ragflow',
  `base_url` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API地址',
  `api_key` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API Key',
  `chat_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'RAGFlow聊天助手ID',
  `model_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模型名称',
  `timeout_seconds` int DEFAULT NULL COMMENT '请求超时秒数',
  `ragflow_base_url` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'RAGFlow知识库API地址',
  `ragflow_api_key` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'RAGFlow API Key',
  `ragflow_dataset_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'RAGFlow知识库ID',
  `ragflow_dataset_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'RAGFlow知识库名称',
  `ragflow_auto_upload` tinyint(1) DEFAULT NULL COMMENT '上传试卷时同步写入RAGFlow',
  `ragflow_upload_fail_fast` tinyint(1) DEFAULT NULL COMMENT 'RAGFlow上传失败时中断导入',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI接口配置';

INSERT INTO `sys_ai_config` (`id`, `enabled`, `create_time`, `update_time`)
SELECT '1', 1, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_config` WHERE `id` = '1');
