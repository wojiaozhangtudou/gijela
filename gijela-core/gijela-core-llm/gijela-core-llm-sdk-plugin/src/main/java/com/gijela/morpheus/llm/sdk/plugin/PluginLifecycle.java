package com.gijela.morpheus.llm.sdk.plugin;

/**
 * 插件生命周期操作。
 */
public interface PluginLifecycle {

    void load(Plugin plugin);

    void enable(String pluginId);

    void disable(String pluginId);

    void unload(String pluginId);
}
