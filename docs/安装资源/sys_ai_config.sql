-- 已有数据库升级：创建 AI 配置表（每位管理员一条，主键 id = 用户 ID）
-- 新装系统请使用「数据库脚本.sql」，无需执行本文件。
CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI接口配置（按用户）';

-- 若此前已创建全局配置行 id='1'，可手动删除（不影响新逻辑）：
-- DELETE FROM `sys_ai_config` WHERE `id` = '1';
