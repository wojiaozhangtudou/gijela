-- V5: 技能运行态表（启停 + 上次加载状态）
CREATE TABLE IF NOT EXISTS `chat_skill_state` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`      VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '租户标识',
  `name`           VARCHAR(128) NOT NULL COMMENT '技能名',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  `source`         VARCHAR(16)  NOT NULL DEFAULT 'BUILTIN' COMMENT 'BUILTIN/LOCAL',
  `last_loaded_at` DATETIME     NULL COMMENT '上次成功加载时间',
  `error_msg`      VARCHAR(1024) NULL COMMENT '上次加载错误信息',
  `updated_by`     VARCHAR(64)  NULL COMMENT '最近一次操作人',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能运行态';
