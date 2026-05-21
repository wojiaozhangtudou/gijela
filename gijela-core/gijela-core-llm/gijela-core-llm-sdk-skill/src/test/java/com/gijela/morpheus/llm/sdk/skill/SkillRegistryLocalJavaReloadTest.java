package com.gijela.morpheus.llm.sdk.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkillRegistryLocalJavaReloadTest {

    @TempDir
    Path tempDir;

    @Test
    void reload_shouldReplaceLocalJavaProvidersWithoutLeavingBuiltinSource() throws IOException {
        Path skillDir = tempDir.resolve("echo");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: echo
                description: 回显输入内容
                version: 1.0.0
                entry: java:com.gijela.morpheus.llm.sdk.skill.EchoSkillProvider
                timeoutMs: 1000
                retry: 0
                ---
                """, StandardCharsets.UTF_8);

        SkillSdkProperties properties = new SkillSdkProperties();
        properties.setScanEnabled(true);
        properties.setLocalPath(skillDir.getParent().toString());

        SkillRegistry registry = new SkillRegistry(properties, null);
        assertEquals(SkillSource.LOCAL, registry.sourcesSnapshot().get("echo"));
        assertFalse(registry.getProvider("echo").builtin());

        registry.reload();

        assertEquals(1, registry.listAll().size());
        assertNotNull(registry.getProvider("echo"));
        assertEquals(SkillSource.LOCAL, registry.sourcesSnapshot().get("echo"));
        assertFalse(registry.getProvider("echo").builtin());
    }
}