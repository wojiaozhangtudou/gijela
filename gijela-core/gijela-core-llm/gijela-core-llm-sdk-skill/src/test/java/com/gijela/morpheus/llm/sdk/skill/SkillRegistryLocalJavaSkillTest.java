package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryLocalJavaSkillTest {

    @TempDir
    Path tempDir;

    @Test
    void registry_shouldRegisterLocalJavaSkill() throws Exception {
        Path skillDir = tempDir.resolve("dummy-local");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: dummy-local
                description: local skill
                version: 1.0.0
                entry: java:com.gijela.morpheus.llm.sdk.skill.TestLocalSkillProvider
                ---
                """);

        SkillSdkProperties properties = new SkillSdkProperties();
        properties.setScanEnabled(true);
        properties.setLocalPath(tempDir.toString());

        SkillRegistry registry = new SkillRegistry(properties, null);

        List<Map<String, Object>> schemas = registry.resolveToolSchemas(List.of("dummy.local"));
        assertEquals(1, schemas.size());
        assertTrue(registry.listAll().contains("dummy.local"));

        ToolResult result = registry.execute(new ToolCall("call-1", "dummy.local", Map.of()), new ToolContext("t", Map.of()));
        assertTrue(result.success());
        assertEquals(true, result.result().get("ok"));
    }
}
