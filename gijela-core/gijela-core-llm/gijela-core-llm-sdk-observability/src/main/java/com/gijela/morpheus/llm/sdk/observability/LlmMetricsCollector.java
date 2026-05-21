package com.gijela.morpheus.llm.sdk.observability;

/**
 * 指标采集抽象。
 */
public interface LlmMetricsCollector {

    void recordSuccess(String model, long latencyMs);

    void recordFailure(String model, String errorCode);
}
