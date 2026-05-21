ALTER TABLE `chat_attachment`
  ADD COLUMN `raw_text`       LONGTEXT     DEFAULT NULL COMMENT '提取的文件原始文本',
  ADD COLUMN `condensed_md`   LONGTEXT     DEFAULT NULL COMMENT 'LLM浓缩后的Markdown（原文约30%）',
  ADD COLUMN `summary`        VARCHAR(500) DEFAULT NULL COMMENT '一句话摘要（50字以内）',
  ADD COLUMN `process_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待处理 1=处理中 2=完成 3=失败';
