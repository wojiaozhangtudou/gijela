ALTER TABLE `chat_message`
  ADD COLUMN `references_json` LONGTEXT DEFAULT NULL COMMENT 'assistant消息关联知识库引用(JSON数组)';
