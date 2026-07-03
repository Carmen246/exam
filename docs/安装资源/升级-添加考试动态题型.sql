ALTER TABLE `el_exam_repo`
  ADD COLUMN `type_config` text COLLATE utf8mb4_general_ci COMMENT '题型配置JSON' AFTER `judge_score`;
