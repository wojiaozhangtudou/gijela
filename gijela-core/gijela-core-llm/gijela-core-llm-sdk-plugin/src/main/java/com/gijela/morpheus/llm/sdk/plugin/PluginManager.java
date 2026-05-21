package com.gijela.morpheus.llm.sdk.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件管理器（骨架）。
 */
public class PluginManager implements PluginLifecycle {

    private final PluginRegistry registry;
    private final Map<String, PluginState> states = new ConcurrentHashMap<>();

    public PluginManager(PluginRegistry registry) {
        this.registry = registry;
    }

    public void load(Plugin plugin) {
        plugin.onLoad();
        registry.register(plugin);
        states.put(plugin.id(), PluginState.LOADED);
    }

    public void enable(String pluginId) {
        registry.get(pluginId).ifPresent(plugin -> {
            plugin.onEnable();
            states.put(pluginId, PluginState.ENABLED);
        });
    }

    public void disable(String pluginId) {
        registry.get(pluginId).ifPresent(plugin -> {
            plugin.onDisable();
            states.put(pluginId, PluginState.DISABLED);
        });
    }

    public void unload(String pluginId) {
        registry.get(pluginId).ifPresent(Plugin::onUnload);
        registry.unregister(pluginId);
        states.put(pluginId, PluginState.UNLOADED);
    }

    public PluginState stateOf(String pluginId) {
        return states.get(pluginId);
    }
}
