package com.gijela.morpheus.llm.sdk.mcp.skill;

import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpSkillSync;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBindingSource;
import com.gijela.morpheus.llm.sdk.mcp.McpSdkProperties;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import com.gijela.morpheus.llm.sdk.skill.SkillSdkProperties;
import com.gijela.morpheus.llm.sdk.skill.SkillSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

class McpSkillSyncTest {
    private SkillRegistry registry;
    private McpToolBindingSource bindingSource;
    private McpJsonRpcClient mcpClient;
    private McpSdkProperties properties;

    McpSkillSyncTest() {
    }

    @BeforeEach
    void setUp() {
        this.properties = new McpSdkProperties();
        this.properties.setAutoExposeToSkills(true);
        this.properties.setMaxToolResponseChars(25000);
        SkillSdkProperties skillProps = new SkillSdkProperties();
        skillProps.setScanEnabled(false);
        this.registry = new SkillRegistry(skillProps, null);
        this.bindingSource = (McpToolBindingSource)Mockito.mock(McpToolBindingSource.class);
        this.mcpClient = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
    }

    private static McpToolBinding binding(String fqName, String server, String tool) {
        return new McpToolBinding(fqName, server, tool, "desc", Map.of(), McpEndpointConfig.http((String)"http://localhost/mcp", null));
    }

    @Test
    void syncAll_shouldRegisterMcpProvidersIntoSkillRegistry() {
        Mockito.when((Object)this.bindingSource.loadActiveBindings((String)ArgumentMatchers.isNull())).thenReturn(List.of(McpSkillSyncTest.binding("mcp__demo__a", "demo", "a"), McpSkillSyncTest.binding("mcp__demo__b", "demo", "b")));
        McpSkillSync sync = new McpSkillSync(this.bindingSource, this.registry, this.mcpClient, this.properties);
        int n = sync.syncAll();
        Assertions.assertEquals((int)2, (int)n);
        Assertions.assertTrue((boolean)this.registry.listAll().contains("mcp__demo__a"));
        Assertions.assertTrue((boolean)this.registry.listAll().contains("mcp__demo__b"));
        Assertions.assertEquals((Object)SkillSource.MCP, this.registry.sourcesSnapshot().get("mcp__demo__a"));
    }

    @Test
    void syncAll_shouldReplaceOldMcpProvidersOnSecondCall() {
        Mockito.when((Object)this.bindingSource.loadActiveBindings((String)ArgumentMatchers.isNull())).thenReturn(List.of(McpSkillSyncTest.binding("mcp__demo__old", "demo", "old"))).thenReturn(List.of(McpSkillSyncTest.binding("mcp__demo__new", "demo", "new")));
        McpSkillSync sync = new McpSkillSync(this.bindingSource, this.registry, this.mcpClient, this.properties);
        sync.syncAll();
        Assertions.assertTrue((boolean)this.registry.listAll().contains("mcp__demo__old"));
        sync.syncAll();
        Assertions.assertFalse((boolean)this.registry.listAll().contains("mcp__demo__old"));
        Assertions.assertTrue((boolean)this.registry.listAll().contains("mcp__demo__new"));
        ((McpToolBindingSource)Mockito.verify((Object)this.bindingSource, (VerificationMode)Mockito.times((int)2))).loadActiveBindings((String)ArgumentMatchers.isNull());
    }

    @Test
    void syncAll_shouldClearAllMcpWhenAutoExposeDisabled() {
        Mockito.when((Object)this.bindingSource.loadActiveBindings((String)ArgumentMatchers.isNull())).thenReturn(List.of(McpSkillSyncTest.binding("mcp__demo__a", "demo", "a")));
        McpSkillSync sync = new McpSkillSync(this.bindingSource, this.registry, this.mcpClient, this.properties);
        sync.syncAll();
        Assertions.assertTrue((boolean)this.registry.listAll().contains("mcp__demo__a"));
        this.properties.setAutoExposeToSkills(false);
        int n = sync.syncAll();
        Assertions.assertEquals((int)0, (int)n);
        Assertions.assertFalse((boolean)this.registry.listAll().contains("mcp__demo__a"));
        ((McpToolBindingSource)Mockito.verify((Object)this.bindingSource, (VerificationMode)Mockito.times((int)1))).loadActiveBindings((String)ArgumentMatchers.isNull());
    }

    @Test
    void syncAll_shouldHandleEmptyBindings() {
        Mockito.when((Object)this.bindingSource.loadActiveBindings((String)ArgumentMatchers.isNull())).thenReturn(List.of());
        McpSkillSync sync = new McpSkillSync(this.bindingSource, this.registry, this.mcpClient, this.properties);
        Assertions.assertEquals((int)0, (int)sync.syncAll());
    }

    @Test
    void initOnStartup_shouldSkipWhenAutoExposeDisabled() {
        this.properties.setAutoExposeToSkills(false);
        McpSkillSync sync = new McpSkillSync(this.bindingSource, this.registry, this.mcpClient, this.properties);
        sync.initOnStartup();
        ((McpToolBindingSource)Mockito.verify((Object)this.bindingSource, (VerificationMode)Mockito.never())).loadActiveBindings((String)ArgumentMatchers.isNull());
    }

    @Test
    void initOnStartup_shouldSwallowExceptionsAndNotPropagate() {
        Mockito.when((Object)this.bindingSource.loadActiveBindings((String)ArgumentMatchers.isNull())).thenThrow(new Throwable[]{new RuntimeException("db down")});
        McpSkillSync sync = new McpSkillSync(this.bindingSource, this.registry, this.mcpClient, this.properties);
        sync.initOnStartup();
    }
}
