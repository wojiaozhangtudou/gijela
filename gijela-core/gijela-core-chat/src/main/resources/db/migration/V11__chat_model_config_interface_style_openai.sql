UPDATE `chat_model_config`
SET `provider_key` = 'openai'
WHERE `provider_key` IS NULL OR `provider_key` <> 'openai';

ALTER TABLE `chat_model_config`
  MODIFY COLUMN `provider_key` VARCHAR(64) NOT NULL DEFAULT 'openai' COMMENT '接口风格，固定为 openai';
