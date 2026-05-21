package com.gijela.morpheus.llm.sdk.plugin;

/**
 * 插件状态机占位实现。
 */
public class PluginStateMachine {

    public boolean canTransit(PluginState from, PluginState to) {
        return switch (from) {
            case LOADED -> to == PluginState.ENABLED || to == PluginState.UNLOADED;
            case ENABLED -> to == PluginState.DISABLED;
            case DISABLED -> to == PluginState.ENABLED || to == PluginState.UNLOADED;
            case UNLOADED -> false;
        };
    }
}
