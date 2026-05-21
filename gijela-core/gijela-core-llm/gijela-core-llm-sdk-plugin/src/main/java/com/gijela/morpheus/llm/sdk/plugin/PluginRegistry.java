package com.gijela.morpheus.llm.sdk.plugin;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册表。
 */
public class PluginRegistry {

    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();

    public void register(Plugin plugin) {
        plugins.put(plugin.id(), plugin);
    }

    public Optional<Plugin> get(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    public void unregister(String pluginId) {
        plugins.remove(pluginId);
    }
}
