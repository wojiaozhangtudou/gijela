package com.gijela.morpheus.llm.sdk.plugin;

/**
 * 插件回滚策略占位。
 */
public class PluginRollbackPolicy {

    public boolean shouldRollback(double errorRate, long timeoutSpikeCount) {
        return errorRate >= 0.5 || timeoutSpikeCount > 0;
    }
}
