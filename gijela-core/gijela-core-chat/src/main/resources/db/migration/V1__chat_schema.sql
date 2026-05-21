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
