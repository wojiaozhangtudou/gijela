package com.gijela.morpheus.chat.llm.log.es;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ES 客户端 Mock（Day1 用）
 * 目的：在真实 ES 部署前，提供 Mock 实现便于前后端独立开发
 * 
 * 配置：在 application.yml 中设置
 * app.llm.es.enabled: false  # 禁用真实 ES，使用 Mock
 * 
 * Day2 将替换为真实 ES 实现
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "app.llm.es.enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class EsClientMock {
    
    /**
     * Mock：获取集群健康状态
     */
    public boolean isClusterHealthy() {
        log.debug("Mock: ES cluster is healthy");
        return true;
    }
    
    /**
     * Mock：查询时间序列数据
     */
    public Object mockTimeSeriesQuery(String metric, String interval) {
        log.debug("Mock: TimeSeriesQuery - metric={}, interval={}", metric, interval);

        return java.util.Map.of(
                "status", "mock",
                "buckets", java.util.Collections.emptyList());
    }
    
    /**
     * Mock：查询记录
     */
    public Object mockRecordsQuery(String filter) {
        log.debug("Mock: RecordsQuery - filter={}", filter);

        return java.util.Map.of(
                "status", "mock",
                "records", java.util.Collections.emptyList(),
                "total", 0L);
    }
    
    /**
     * Mock：链路追踪
     */
    public Object mockTraceQuery(String traceId) {
        log.debug("Mock: TraceQuery - traceId={}", traceId);

        return java.util.Map.of(
                "traceId", traceId,
                "spans", java.util.Collections.emptyList());
    }
    
    /**
     * 获取 ES 版本（Mock）
     */
    public String getVersion() {
        return "7.17.0-mock";
    }
}
