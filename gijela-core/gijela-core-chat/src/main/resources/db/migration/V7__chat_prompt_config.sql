-- V7: 提示词配置（系统级 + 会话级 + 审计）

CREATE TABLE IF NOT EXISTS `chat_prompt_system` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`          VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '租户标识',
  `app_code`           VARCHAR(64)  NOT NULL COMMENT '应用编码（如 chat）',
  `model_route`        VARCHAR(64)  NOT NULL COMMENT '模型路由（如 default）',
  `draft_content`      LONGTEXT     NULL COMMENT '草稿内容',
  `draft_version`      BIGINT       NOT NULL DEFAULT 0 COMMENT '草稿版本号',
  `published_content`  LONGTEXT     NULL COMMENT '已发布内容',
  `published_version`  BIGINT       NOT NULL DEFAULT 0 COMMENT '发布版本号',
  `published_at`       DATETIME     NULL COMMENT '发布时间',
  `published_by`       VARCHAR(64)  NULL COMMENT '发布人',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`         VARCHAR(64)  NULL,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`         VARCHAR(64)  NULL,
  `deleted`            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=有效 1=删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_system_scope` (`tenant_id`, `app_code`, `model_route`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统级提示词配置';

CREATE TABLE IF NOT EXISTS `chat_prompt_system_audit` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`        VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '租户标识',
  `app_code`         VARCHAR(64)  NOT NULL COMMENT '应用编码（如 chat）',
  `model_route`      VARCHAR(64)  NOT NULL COMMENT '模型路由（如 default）',
  `action`           VARCHAR(32)  NOT NULL COMMENT 'SAVE_DRAFT | PUBLISH | ROLLBACK',
  `before_version`   BIGINT       NOT NULL DEFAULT 0 COMMENT '变更前版本',
  `after_version`    BIGINT       NOT NULL DEFAULT 0 COMMENT '变更后版本',
  `before_content`   LONGTEXT     NULL COMMENT '变更前内容',
  `after_content`    LONGTEXT     NULL COMMENT '变更后内容',
  `operator_id`      VARCHAR(64)  NULL COMMENT '操作人ID',
  `operator_name`    VARCHAR(64)  NULL COMMENT '操作人名称',
  `diff_summary`     VARCHAR(512) NULL COMMENT 'Diff摘要',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prompt_audit_scope_time` (`tenant_id`, `app_code`, `model_route`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统级提示词审计记录';

CREATE TABLE IF NOT EXISTS `chat_session_prompt` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`        VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '租户标识',
  `session_id`       VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `content`          LONGTEXT     NULL COMMENT '会话级提示词内容',
  `version`          BIGINT       NOT NULL DEFAULT 0 COMMENT '会话提示词版本号',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`       VARCHAR(64)  NULL,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`       VARCHAR(64)  NULL,
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=有效 1=删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_prompt_scope` (`tenant_id`, `session_id`),
  KEY `idx_session_prompt_updated` (`tenant_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话级提示词配置';
