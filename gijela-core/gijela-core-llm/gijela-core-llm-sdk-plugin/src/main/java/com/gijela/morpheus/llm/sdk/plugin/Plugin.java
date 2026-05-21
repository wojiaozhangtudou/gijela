package com.gijela.morpheus.llm.sdk.plugin;

import java.util.Set;

/**
 * 插件 SPI。
 */
public interface Plugin {

    String id();

    String version();

    Set<PluginCapability> capabilities();

    default void onLoad() {
    }

    default void onEnable() {
    }

    default void onDisable() {
    }

    default void onUnload() {
    }
}
