package com.gijela.morpheus.llm.sdk.mcp.skill;

import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBindingSource;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolSkillProvider;
import com.gijela.morpheus.llm.sdk.mcp.McpSdkProperties;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpSkillSync {
    private static final Logger log = LoggerFactory.getLogger(McpSkillSync.class);
    private final McpToolBindingSource bindingSource;
    private final SkillRegistry skillRegistry;
    private final McpJsonRpcClient mcpClient;
    private final McpSdkProperties properties;

    public McpSkillSync(McpToolBindingSource bindingSource, SkillRegistry skillRegistry, McpJsonRpcClient mcpClient, McpSdkProperties properties) {
        this.bindingSource = bindingSource;
        this.skillRegistry = skillRegistry;
        this.mcpClient = mcpClient;
        this.properties = properties;
    }

    public void initOnStartup() {
        if (!this.properties.isAutoExposeToSkills()) {
            log.info("[mcp-skill] auto-expose disabled (gijela.llm.mcp.auto-expose-to-skills=false), skip initial sync");
            return;
        }
        try {
            int n = this.syncAll();
            log.info("[mcp-skill] startup sync done, exposed {} mcp tools", (Object)n);
        }
        catch (Exception e) {
            log.warn("[mcp-skill] startup sync failed: {}", (Object)e.getMessage(), (Object)e);
        }
    }

    public synchronized int syncAll() {
        if (!this.properties.isAutoExposeToSkills()) {
            this.skillRegistry.replaceMcpProviders(List.of());
            return 0;
        }
        List<McpToolBinding> bindings = this.bindingSource.loadActiveBindings(null);
        int maxResp = this.properties.getMaxToolResponseChars();
        List<McpToolSkillProvider> providers = bindings.stream().map(b -> new McpToolSkillProvider((McpToolBinding)b, this.mcpClient, maxResp)).toList();
        this.skillRegistry.replaceMcpProviders(providers);
        return providers.size();
    }
}
