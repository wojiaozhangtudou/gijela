package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryReplaceMcpTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        SkillSdkProperties properties = new SkillSdkProperties();
        properties.setScanEnabled(false);
        registry = new SkillRegistry(properties, null);
        // 业务侧手动注册一个 BUILTIN 作为名字冲突用例
        registry.register(new TimeNowSkillProvider());
    }

    @Test
    void replaceMcpProviders_shouldRegisterNewMcpProviders() {
        registry.replaceMcpProviders(List.of(
                new FakeMcpProvider("mcp__a__t1"),
                new FakeMcpProvider("mcp__a__t2")));

        assertTrue(registry.listAll().contains("mcp__a__t1"));
        assertTrue(registry.listAll().contains("mcp__a__t2"));
        assertEquals(SkillSource.MCP, registry.sourcesSnapshot().get("mcp__a__t1"));
    }

    @Test
    void replaceMcpProviders_shouldRemoveOldMcpProvidersOnReplace() {
        registry.replaceMcpProviders(List.of(new FakeMcpProvider("mcp__a__old")));
        assertTrue(registry.listAll().contains("mcp__a__old"));

        registry.replaceMcpProviders(List.of(new FakeMcpProvider("mcp__a__new")));

        assertFalse(registry.listAll().contains("mcp__a__old"), "旧 MCP provider 应被清除");
        assertTrue(registry.listAll().contains("mcp__a__new"));
    }

    @Test
    void replaceMcpProviders_shouldKeepBuiltinSkills() {
        int builtinCount = registry.listAll().size();
        assertEquals(1, builtinCount);
        assertTrue(registry.listAll().contains("time.now"));

        registry.replaceMcpProviders(List.of(new FakeMcpProvider("mcp__a__t1")));
        assertEquals(builtinCount + 1, registry.listAll().size());
        assertTrue(registry.listAll().contains("time.now"));

        registry.replaceMcpProviders(List.of());
        assertEquals(builtinCount, registry.listAll().size());
        assertTrue(registry.listAll().contains("time.now"));
    }

    @Test
    void replaceMcpProviders_shouldSkipNameConflictWithBuiltin() {
        registry.replaceMcpProviders(List.of(new FakeMcpProvider("time.now")));
        assertEquals(SkillSource.BUILTIN, registry.sourcesSnapshot().get("time.now"));
    }

    @Test
    void execute_shouldDispatchToMcpProvider() {
        registry.replaceMcpProviders(List.of(new FakeMcpProvider("mcp__a__echo")));
        ToolResult r = registry.execute(
                new ToolCall("c1", "mcp__a__echo", Map.of("x", 1)),
                new ToolContext("t", Map.of()));
        assertTrue(r.success());
        assertEquals("mcp__a__echo", r.result().get("name"));
    }

    @Test
    void getSchema_shouldReturnNullForUnknownAndPresentForRegistered() {
        assertNull(registry.getSchema("nope"));
        registry.replaceMcpProviders(List.of(new FakeMcpProvider("mcp__a__present")));
        assertNotNull(registry.getSchema("mcp__a__present"));
    }

    private static class FakeMcpProvider implements SkillProvider {
        private final String name;

        FakeMcpProvider(String name) { this.name = name; }

        @Override public String name() { return name; }

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", "function",
                    "function", Map.of("name", name, "description", "fake", "parameters",
                            Map.of("type", "object", "properties", Map.of(), "required", List.of())));
        }

        @Override
        public ToolResult execute(ToolCall call, ToolContext context) {
            return new ToolResult(call.id(), true, Map.of("name", name), null);
        }

        @Override public SkillSource source() { return SkillSource.MCP; }

        @Override public boolean builtin() { return false; }
    }
}
