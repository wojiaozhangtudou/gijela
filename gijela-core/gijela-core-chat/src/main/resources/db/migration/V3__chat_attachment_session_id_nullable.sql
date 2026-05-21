ALTER TABLE `chat_attachment`
  MODIFY COLUMN `session_id` VARCHAR(64) DEFAULT NULL COMMENT '关联会话ID，上传时未建会话则为空';
