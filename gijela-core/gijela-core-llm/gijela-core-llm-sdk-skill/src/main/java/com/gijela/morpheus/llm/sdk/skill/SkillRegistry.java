package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.client.ToolExecutor;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 注册表：聚合 BUILTIN/LOCAL/MCP 三类技能。
 *
 * <p>注意：本类不绑定任何业务，业务侧 builtin provider 需在启动时显式 {@link #register(SkillProvider)}。</p>
 */
public class SkillRegistry implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, SkillProvider> providers;
    private volatile List<SkillManifest> localManifests;
    private final AutowireCapableBeanFactory beanFactory;
    private final SkillSdkProperties properties;

    public SkillRegistry(SkillSdkProperties properties,
                         AutowireCapableBeanFactory beanFactory) {
        this.providers = new LinkedHashMap<>();
        this.beanFactory = beanFactory;
        this.properties = properties;
        this.localManifests = loadLocalManifests(properties);
        registerLocalProviders();
    }

    /** 返回所有已注册技能的 schema（忽略 requestedSkills 参数，始终加载全部）。 */
    public List<Map<String, Object>> resolveToolSchemas(List<String> requestedSkills) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (SkillProvider provider : providers.values()) {
            tools.add(provider.schema());
        }
        return tools;
    }

    public List<String> listAll() {
        return List.copyOf(providers.keySet());
    }

    public SkillProvider getProvider(String name) {
        return providers.get(name);
    }

    public Map<String, Object> getSchema(String name) {
        SkillProvider p = providers.get(name);
        return p == null ? null : p.schema();
    }

    /** 查找本地技能的 manifest（内置技能返回 null）。 */
    public SkillManifest findManifest(String name) {
        if (localManifests == null) return null;
        for (SkillManifest m : localManifests) {
            if (m.name().equals(name)) return m;
        }
        return null;
    }

    /** 返回当前所有技能的名称 → 来源。 */
    public Map<String, SkillSource> sourcesSnapshot() {
        Map<String, SkillSource> map = new LinkedHashMap<>();
        for (Map.Entry<String, SkillProvider> e : providers.entrySet()) {
            map.put(e.getKey(), e.getValue().source());
        }
        return map;
    }

    /**
     * 热加载：重新扫描本地技能目录，注册新发现的技能，移除已不存在的技能。
     * 仅清理 source=LOCAL 的 provider，保留 BUILTIN 与 MCP。
     */
    public synchronized void reload() {
        List<SkillManifest> fresh = loadLocalManifests(properties);
        providers.entrySet().removeIf(e -> e.getValue().source() == SkillSource.LOCAL);
        this.localManifests = fresh;
        registerLocalProviders();
        logger.info("[skills] hot-reload complete, total providers={}, names={}", providers.size(), providers.keySet());
    }

    /**
     * 整体替换 source=MCP 的 provider 集合（CoW 风格）：先移除所有 source=MCP 的 provider，
     * 再注册新提供的 MCP provider 列表。命名冲突时新 MCP provider 让位（保留已有 BUILTIN/LOCAL）。
     */
    public synchronized void replaceMcpProviders(List<? extends SkillProvider> mcpProviders) {
        providers.entrySet().removeIf(e -> e.getValue().source() == SkillSource.MCP);
        int added = 0, skipped = 0;
        for (SkillProvider p : mcpProviders) {
            if (providers.containsKey(p.name())) {
                logger.warn("[skills] mcp provider name conflicts with existing skill, skip: {}", p.name());
                skipped++;
                continue;
            }
            providers.put(p.name(), p);
            added++;
        }
        logger.info("[skills] mcp providers refreshed, added={}, skipped={}, total={}",
                added, skipped, providers.size());
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        try {
            SkillProvider provider = providers.get(call.name());
            if (provider != null) {
                return provider.execute(call, context);
            }
            return new ToolResult(call.id(), false, Map.of(), "未注册的技能: " + call.name());
        } catch (Exception e) {
            return new ToolResult(call.id(), false, Map.of(), "技能执行失败: " + e.getMessage());
        }
    }

    /** 注册一个技能（幂等：同名会被覆盖）。业务侧启动时调用。 */
    public void register(SkillProvider provider) {
        this.providers.put(provider.name(), provider);
    }

    private void registerLocalProviders() {
        if (localManifests.isEmpty()) {
            return;
        }
        for (SkillManifest manifest : localManifests) {
            String entry = manifest.entry();
            if (entry == null || entry.isBlank()) {
                logger.warn("[skills] skip local skill: empty entry, name={}", manifest.name());
                continue;
            }
            if (entry.startsWith("script:")) {
                SkillProvider provider = new ScriptSkillProvider(manifest);
                if (providers.containsKey(provider.name())) {
                    logger.warn("[skills] duplicate provider name, keep existing and skip local one, name={}, entry={}",
                            provider.name(), entry);
                    continue;
                }
                register(provider);
                logger.info("[skills] local script skill registered, name={}, entry={}, version={}",
                        provider.name(), entry, manifest.version());
                continue;
            }
            if (!entry.startsWith("java:")) {
                logger.warn("[skills] unsupported skill entry prefix, name={}, entry={}", manifest.name(), entry);
                continue;
            }

            String className = entry.substring("java:".length()).trim();
            if (className.isBlank()) {
                logger.warn("[skills] invalid java entry (empty class), name={}", manifest.name());
                continue;
            }

            try {
                Class<?> rawType = Class.forName(className);
                if (!SkillProvider.class.isAssignableFrom(rawType)) {
                    logger.warn("[skills] class is not SkillProvider, name={}, class={}", manifest.name(), className);
                    continue;
                }
                SkillProvider provider = instantiateProvider(rawType, manifest, className);
                if (provider == null) {
                    continue;
                }
                provider = wrapAsLocalProvider(provider);
                if (providers.containsKey(provider.name())) {
                    logger.warn("[skills] duplicate provider name, keep existing and skip local one, name={}, class={}",
                            provider.name(), className);
                    continue;
                }
                register(provider);
                logger.info("[skills] local java skill registered, name={}, manifestName={}, class={}, version={}",
                        provider.name(), manifest.name(), className, manifest.version());
            } catch (Exception e) {
                logger.warn("[skills] failed to register local java skill, name={}, class={}, err={}",
                        manifest.name(), className, e.getMessage());
            }
        }
    }

    private SkillProvider instantiateProvider(Class<?> rawType, SkillManifest manifest, String className) {
        try {
            if (beanFactory != null) {
                return (SkillProvider) beanFactory.createBean(rawType);
            }
        } catch (Exception e) {
            logger.warn("[skills] spring createBean failed, fallback to no-arg constructor, name={}, class={}, err={}",
                    manifest.name(), className, e.getMessage());
        }
        try {
            return (SkillProvider) rawType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.warn("[skills] failed to instantiate local java skill, name={}, class={}, err={}",
                    manifest.name(), className, e.getMessage());
            return null;
        }
    }

    private SkillProvider wrapAsLocalProvider(SkillProvider delegate) {
        if (delegate == null || delegate.source() == SkillSource.LOCAL) {
            return delegate;
        }
        return new LocalSkillProviderAdapter(delegate);
    }

    private List<SkillManifest> loadLocalManifests(SkillSdkProperties properties) {
        if (properties == null || !properties.isScanEnabled()) {
            return List.of();
        }
        LocalSkillPackageLoader loader = new LocalSkillPackageLoader();
        List<SkillManifest> manifests = loader.load(properties.getLocalPath());
        if (!manifests.isEmpty()) {
            logger.info("[skills] 本地技能包扫描完成, count={}, path={}", manifests.size(), properties.getLocalPath());
            for (SkillManifest manifest : manifests) {
                logger.info("[skills] detected skill package: name={}, version={}, entry={}",
                        manifest.name(), manifest.version(), manifest.entry());
            }
        }
        return manifests;
    }

    public List<SkillManifest> localManifests() {
        return List.copyOf(localManifests);
    }

    private static final class LocalSkillProviderAdapter implements SkillProvider {
        private final SkillProvider delegate;

        private LocalSkillProviderAdapter(SkillProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public Map<String, Object> schema() {
            return delegate.schema();
        }

        @Override
        public ToolResult execute(ToolCall call, ToolContext context) {
            return delegate.execute(call, context);
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public String version() {
            return delegate.version();
        }

        @Override
        public SkillSource source() {
            return SkillSource.LOCAL;
        }

        @Override
        public boolean builtin() {
            return false;
        }
    }
}
