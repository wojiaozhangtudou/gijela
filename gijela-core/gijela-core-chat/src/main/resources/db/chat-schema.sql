-- chat 模块基础表结构（MySQL 8+）

CREATE TABLE IF NOT EXISTS `chat_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL,
  `session_id` VARCHAR(64) NOT NULL,
  `title` VARCHAR(255) DEFAULT NULL,
  `model` VARCHAR(128) DEFAULT NULL,
  `summary` TEXT,
  `message_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_conv_tenant_session` (`tenant_id`, `session_id`),
  KEY `idx_chat_conv_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL,
  `session_id` VARCHAR(64) NOT NULL,
  `role` VARCHAR(32) NOT NULL,
  `content` LONGTEXT NOT NULL,
  `references_json` LONGTEXT DEFAULT NULL,
  `token_count` INT DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_msg_tenant_session_id` (`tenant_id`, `session_id`, `id`),
  KEY `idx_chat_msg_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL,
  `request_id` VARCHAR(64) DEFAULT NULL,
  `session_id` VARCHAR(64) DEFAULT NULL,
  `action` VARCHAR(128) NOT NULL,
  `result` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_audit_tenant_created` (`tenant_id`, `created_at`),
  KEY `idx_chat_audit_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL,
  `session_id` VARCHAR(64) NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `object_key` VARCHAR(512) NOT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `content_type` VARCHAR(128) DEFAULT NULL,
  `provider` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_attach_tenant_session` (`tenant_id`, `session_id`),
  KEY `idx_chat_attach_object_key` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_prompt_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `app_code` VARCHAR(64) NOT NULL,
  `model_route` VARCHAR(64) NOT NULL,
  `draft_content` LONGTEXT,
  `draft_version` BIGINT NOT NULL DEFAULT 0,
  `published_content` LONGTEXT,
  `published_version` BIGINT NOT NULL DEFAULT 0,
  `published_at` DATETIME DEFAULT NULL,
  `published_by` VARCHAR(64) DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_system_scope` (`tenant_id`, `app_code`, `model_route`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_prompt_system_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `app_code` VARCHAR(64) NOT NULL,
  `model_route` VARCHAR(64) NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `before_version` BIGINT NOT NULL DEFAULT 0,
  `after_version` BIGINT NOT NULL DEFAULT 0,
  `before_content` LONGTEXT,
  `after_content` LONGTEXT,
  `operator_id` VARCHAR(64) DEFAULT NULL,
  `operator_name` VARCHAR(64) DEFAULT NULL,
  `diff_summary` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prompt_audit_scope_time` (`tenant_id`, `app_code`, `model_route`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_session_prompt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `session_id` VARCHAR(64) NOT NULL,
  `content` LONGTEXT,
  `version` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_prompt_scope` (`tenant_id`, `session_id`),
  KEY `idx_session_prompt_updated` (`tenant_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_prompt_system_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `app_code` VARCHAR(64) NOT NULL,
  `model_route` VARCHAR(64) NOT NULL,
  `prompt_name` VARCHAR(64) NOT NULL,
  `content` LONGTEXT,
  `priority` INT NOT NULL DEFAULT 100,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `version` BIGINT NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_prompt_item_scope_name` (`tenant_id`, `app_code`, `model_route`, `prompt_name`),
  KEY `idx_system_prompt_item_scope_order` (`tenant_id`, `app_code`, `model_route`, `enabled`, `priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_session_prompt_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `session_id` VARCHAR(64) NOT NULL,
  `prompt_name` VARCHAR(64) NOT NULL,
  `content` LONGTEXT,
  `priority` INT NOT NULL DEFAULT 100,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `version` BIGINT NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_prompt_item_scope_name` (`tenant_id`, `session_id`, `prompt_name`),
  KEY `idx_session_prompt_item_scope_order` (`tenant_id`, `session_id`, `enabled`, `priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_model_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `config_type` VARCHAR(32) NOT NULL COMMENT 'CHAT/EMBEDDING',
  `provider_key` VARCHAR(64) NOT NULL DEFAULT 'openai' COMMENT '接口风格，固定为 openai',
  `model` VARCHAR(128) NOT NULL COMMENT '模型名',
  `base_url` VARCHAR(512) NOT NULL,
  `api_key` VARCHAR(512) DEFAULT NULL,
  `connect_timeout_seconds` INT NOT NULL DEFAULT 10,
  `read_timeout_seconds` INT NOT NULL DEFAULT 60,
  `call_timeout_seconds` INT NOT NULL DEFAULT 120,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_model_cfg_scope` (`tenant_id`, `config_type`, `provider_key`, `model`),
  KEY `idx_chat_model_cfg_list` (`tenant_id`, `config_type`, `enabled`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
