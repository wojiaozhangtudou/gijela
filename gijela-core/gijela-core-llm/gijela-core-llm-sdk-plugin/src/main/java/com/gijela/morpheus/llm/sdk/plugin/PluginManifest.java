package com.gijela.morpheus.llm.sdk.plugin;

import java.util.Set;

/**
 * 插件元信息。
 */
public record PluginManifest(String id, String version, Set<PluginCapability> capabilities) {
}
