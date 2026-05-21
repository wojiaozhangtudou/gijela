package com.gijela.morpheus.chat.service;

import java.util.Map;

/**
 * 附件摘要提炼服务。
 * 异步读取附件 raw_text，调用 LLM 生成 condensed_md 和 summary，更新 process_status。
 */
public interface AttachmentSummaryService {

    /**
     * 异步触发摘要提炼任务。
     *
     * @param attachmentId chat_attachment.id
     */
    void processAsync(Long attachmentId, String llmModel);

    /**
     * 查询处理状态及摘要内容。
     *
     * @param attachmentId chat_attachment.id
     * @return 包含 status / summary / condensedMd（status=2时）等字段
     */
    Map<String, Object> getStatus(Long attachmentId);
}
