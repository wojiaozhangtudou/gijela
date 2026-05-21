package com.gijela.morpheus.chat.llm.log.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.llm.log.dto.LlmAccessLogVO;
import com.gijela.morpheus.chat.llm.log.dto.LlmAuditLogVO;
import com.gijela.morpheus.chat.llm.log.dto.LlmMetricLogVO;
import com.gijela.morpheus.chat.llm.log.dto.LlmRuntimeLogVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * LLM 日志事件发布器
 * 职责：
 * - 统一收集各类日志事件
 * - 序列化为 JSON 格式
 * - 通过 Logback 写入 stdout（Logback 配置指向 ES ingest pipeline）
 * - 支持同步和异步发布
 */
@Slf4j
@Component
public class LlmLogEventPublisher {

    private static final DateTimeFormatter INDEX_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    
    private final ObjectMapper objectMapper;
    private final RestHighLevelClient restHighLevelClient;

    public LlmLogEventPublisher(ObjectMapper objectMapper,
                                @Autowired(required = false) RestHighLevelClient restHighLevelClient) {
        this.objectMapper = objectMapper;
        this.restHighLevelClient = restHighLevelClient;
    }
    
    /**
     * 发布访问日志（同步）
     * 每次用户请求完成后调用
     */
    public void publishAccessLog(LlmAccessLogVO accessLog) {
        try {
            String json = objectMapper.writeValueAsString(accessLog);
            // Logback 配置中会自动将此日志通过 ingest pipeline 写入 ES
            log.info("ACCESS_LOG: {}", json);
            writeToEs("llm-access", json);
        } catch (Exception e) {
            log.error("Failed to publish access log", e);
        }
    }
    
    /**
     * 发布访问日志（异步）
     * 对于高吞吐量场景，使用异步避免阻塞业务线程
     */
    @Async("chatSummaryExecutor")
    public void publishAccessLogAsync(LlmAccessLogVO accessLog) {
        publishAccessLog(accessLog);
    }
    
    /**
     * 发布运行时日志（同步）
     * 在编排/工具调用过程中实时发布关键事件
     */
    public void publishRuntimeLog(LlmRuntimeLogVO runtimeLog) {
        try {
            String json = objectMapper.writeValueAsString(runtimeLog);
            log.info("RUNTIME_LOG: {}", json);
            writeToEs("llm-runtime", json);
        } catch (Exception e) {
            log.error("Failed to publish runtime log", e);
        }
    }
    
    /**
     * 发布运行时日志（异步）
     */
    @Async("chatSummaryExecutor")
    public void publishRuntimeLogAsync(LlmRuntimeLogVO runtimeLog) {
        publishRuntimeLog(runtimeLog);
    }
    
    /**
     * 发布审计日志（同步，强制）
     * 审计日志必须同步写入，确保不丢失
     */
    public void publishAuditLog(LlmAuditLogVO auditLog) {
        try {
            String json = objectMapper.writeValueAsString(auditLog);
            log.info("AUDIT_LOG: {}", json);
            writeToEs("llm-audit", json);
        } catch (Exception e) {
            log.error("Failed to publish audit log", e);
        }
    }
    
    /**
     * 发布指标日志（同步）
     * 汇总统计后的指标数据
     */
    public void publishMetricLog(LlmMetricLogVO metricLog) {
        try {
            String json = objectMapper.writeValueAsString(metricLog);
            log.info("METRIC_LOG: {}", json);
            writeToEs("llm-metric", json);
        } catch (Exception e) {
            log.error("Failed to publish metric log", e);
        }
    }
    
    /**
     * 发布指标日志（异步）
     */
    @Async("chatSummaryExecutor")
    public void publishMetricLogAsync(LlmMetricLogVO metricLog) {
        publishMetricLog(metricLog);
    }

    private void writeToEs(String indexPrefix, String json) {
        if (restHighLevelClient == null || json == null || json.isBlank()) {
            return;
        }
        String indexName = indexPrefix + "-" + LocalDate.now().format(INDEX_DATE_FORMATTER);
        try {
            Request request = new Request("POST", "/" + indexName + "/_doc");
            request.setJsonEntity(json);
            request.addParameter("timeout", "1m");
            restHighLevelClient.getLowLevelClient().performRequest(request);
        } catch (Exception ex) {
            log.warn("Failed to index {} into ES index {}: {}", indexPrefix, indexName, ex.getMessage());
        }
    }
}
