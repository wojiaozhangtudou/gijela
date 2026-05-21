-- chat-flow 独立库初始化脚本（MySQL 8+）
-- 执行方式示例：
-- mysql -uroot -p < init-chat-flow.sql

CREATE DATABASE IF NOT EXISTS `gijela_chat_flow`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `gijela_chat_flow`;

-- 流程主表
CREATE TABLE IF NOT EXISTS `cf_workflow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `workflow_code` VARCHAR(64) NOT NULL COMMENT '流程编码',
  `name` VARCHAR(128) NOT NULL COMMENT '流程名称',
  `description` VARCHAR(1024) NULL COMMENT '流程描述',
  `app_type` VARCHAR(32) NOT NULL COMMENT '应用类型：workflow/chatflow',
  `status` VARCHAR(32) NOT NULL COMMENT '状态：draft/published',
  `current_draft_version` INT NOT NULL DEFAULT 1 COMMENT '当前草稿版本（下一个版本号）',
  `current_published_version` INT NULL COMMENT '当前已发布版本号',
  `created_by` VARCHAR(64) NOT NULL DEFAULT 'system',
  `updated_by` VARCHAR(64) NOT NULL DEFAULT 'system',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cf_workflow_code` (`workflow_code`),
  KEY `idx_cf_workflow_status` (`status`),
  KEY `idx_cf_workflow_app_type` (`app_type`),
  KEY `idx_cf_workflow_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow 流程主表';

-- 流程版本表（草稿/发布）
CREATE TABLE IF NOT EXISTS `cf_workflow_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `workflow_id` BIGINT NOT NULL,
  `version_no` INT NOT NULL,
  `version_type` VARCHAR(32) NOT NULL COMMENT 'draft/published',
  `definition_json` LONGTEXT NOT NULL COMMENT '流程定义JSON',
  `published_by` VARCHAR(64) NULL,
  `published_at` DATETIME NULL,
  `created_by` VARCHAR(64) NOT NULL DEFAULT 'system',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cf_wf_ver_type` (`workflow_id`, `version_no`, `version_type`),
  KEY `idx_cf_wf_ver_workflow_id` (`workflow_id`),
  KEY `idx_cf_wf_ver_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow 流程版本表';

-- 运行主表
CREATE TABLE IF NOT EXISTS `cf_workflow_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(64) NOT NULL,
  `workflow_id` BIGINT NOT NULL,
  `workflow_version` INT NOT NULL,
  `run_type` VARCHAR(16) NOT NULL COMMENT 'debug/prod',
  `status` VARCHAR(32) NOT NULL COMMENT 'running/success/failed',
  `trigger_by` VARCHAR(64) NOT NULL DEFAULT 'system',
  `started_at` DATETIME NOT NULL,
  `ended_at` DATETIME NULL,
  `duration_ms` BIGINT NULL,
  `final_result_json` LONGTEXT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` VARCHAR(1024) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cf_workflow_run_run_id` (`run_id`),
  KEY `idx_cf_workflow_run_workflow_id` (`workflow_id`),
  KEY `idx_cf_workflow_run_started_at` (`started_at`),
  KEY `idx_cf_workflow_run_type_status` (`run_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow 运行主表';

-- 节点运行明细表
CREATE TABLE IF NOT EXISTS `cf_workflow_run_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(64) NOT NULL,
  `node_id` VARCHAR(64) NOT NULL,
  `node_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME NULL,
  `ended_at` DATETIME NULL,
  `duration_ms` BIGINT NULL,
  `input_snapshot` LONGTEXT NULL,
  `output_snapshot` LONGTEXT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` VARCHAR(1024) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cf_run_node_run_id` (`run_id`),
  KEY `idx_cf_run_node_node_id` (`node_id`),
  KEY `idx_cf_run_node_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow 节点运行明细表';

-- LLM 模型注册表（模型元数据与调用凭据均由数据库维护）
CREATE TABLE IF NOT EXISTS `cf_llm_model` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `model_key` VARCHAR(64) NOT NULL COMMENT '模型稳定键（业务主键）',
  `display_name` VARCHAR(128) NOT NULL COMMENT '展示名称',
  `provider` VARCHAR(32) NOT NULL COMMENT '供应商标识',
  `base_url` VARCHAR(255) NOT NULL COMMENT 'OpenAI兼容接口URL',
  `api_key` VARCHAR(255) NOT NULL COMMENT 'OpenAI API Key',
  `target_model` VARCHAR(128) NOT NULL COMMENT '调用目标模型名',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1是0否',
  `default_temperature` DECIMAL(4,2) NULL COMMENT '默认温度',
  `default_max_tokens` INT NULL COMMENT '默认最大令牌数',
  `remark` VARCHAR(512) NULL COMMENT '备注',
  `created_by` VARCHAR(64) NOT NULL DEFAULT 'system',
  `updated_by` VARCHAR(64) NOT NULL DEFAULT 'system',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cf_llm_model_key` (`model_key`),
  KEY `idx_cf_llm_model_enabled` (`enabled`),
  KEY `idx_cf_llm_model_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow LLM 模型注册表';

-- 旧库增量迁移：补齐模型 API Key 字段
ALTER TABLE `cf_llm_model`
  ADD COLUMN IF NOT EXISTS `api_key` VARCHAR(255) NULL COMMENT 'OpenAI API Key' AFTER `base_url`;

ALTER TABLE `cf_llm_model`
  MODIFY COLUMN `api_key` VARCHAR(255) NOT NULL COMMENT 'OpenAI API Key';

-- chatflow 会话表（Phase 1）
CREATE TABLE IF NOT EXISTS `cf_chatflow_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(64) NOT NULL,
  `title` VARCHAR(128) NULL,
  `workflow_id` VARCHAR(64) NOT NULL,
  `workflow_inputs` JSON NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'active',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cf_chatflow_session_id` (`session_id`),
  KEY `idx_cf_chatflow_session_workflow_status` (`workflow_id`, `status`, `deleted`),
  KEY `idx_cf_chatflow_session_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow 会话表';

-- chatflow 消息表（Phase 1）
CREATE TABLE IF NOT EXISTS `cf_chatflow_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(64) NOT NULL,
  `role` VARCHAR(16) NOT NULL COMMENT 'user/assistant',
  `content` LONGTEXT NOT NULL,
  `extra_json` JSON NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cf_chatflow_message_session_created` (`session_id`, `created_at`),
  KEY `idx_cf_chatflow_message_session_role_created` (`session_id`, `role`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chat-flow 会话消息表';
