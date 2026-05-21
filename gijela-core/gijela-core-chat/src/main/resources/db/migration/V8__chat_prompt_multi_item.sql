-- V8: 提示词多条配置（系统级 / 会话级）

CREATE TABLE IF NOT EXISTS `chat_prompt_system_item` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`    VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '租户标识',
  `app_code`     VARCHAR(64)  NOT NULL COMMENT '应用编码',
  `model_route`  VARCHAR(64)  NOT NULL COMMENT '模型路由',
  `prompt_name`  VARCHAR(64)  NOT NULL COMMENT '提示词名称（范围内唯一）',
  `content`      LONGTEXT     NULL COMMENT '提示词内容',
  `priority`     INT          NOT NULL DEFAULT 100 COMMENT '优先级（越小越前）',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  `version`      BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`   VARCHAR(64)  NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`   VARCHAR(64)  NULL,
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=有效 1=删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_prompt_item_scope_name` (`tenant_id`, `app_code`, `model_route`, `prompt_name`),
  KEY `idx_system_prompt_item_scope_order` (`tenant_id`, `app_code`, `model_route`, `enabled`, `priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统级多提示词配置';

CREATE TABLE IF NOT EXISTS `chat_session_prompt_item` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`    VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '租户标识',
  `session_id`   VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `prompt_name`  VARCHAR(64)  NOT NULL COMMENT '提示词名称（会话内唯一）',
  `content`      LONGTEXT     NULL COMMENT '提示词内容',
  `priority`     INT          NOT NULL DEFAULT 100 COMMENT '优先级（越小越前）',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  `version`      BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`   VARCHAR(64)  NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`   VARCHAR(64)  NULL,
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=有效 1=删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_prompt_item_scope_name` (`tenant_id`, `session_id`, `prompt_name`),
  KEY `idx_session_prompt_item_scope_order` (`tenant_id`, `session_id`, `enabled`, `priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话级多提示词配置';
