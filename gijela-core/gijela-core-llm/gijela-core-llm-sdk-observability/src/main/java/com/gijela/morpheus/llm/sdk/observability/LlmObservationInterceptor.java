package com.gijela.morpheus.llm.sdk.observability;

/**
 * 统一观测拦截骨架。
 */
public class LlmObservationInterceptor {

    public long before(String model) {
        return System.currentTimeMillis();
    }

    public long after(long start) {
        return System.currentTimeMillis() - start;
    }
}
