package com.gijela.morpheus.llm.sdk.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillManifestParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parse_shouldReadRequiredFields() throws Exception {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: knowledge-search
                description: 检索知识库
                version: 1.0.0
                entry: java:com.example.Skill
                timeoutMs: 3000
                retry: 1
                ---
                # content
                """);

        SkillManifest manifest = new SkillManifestParser().parse(skillMd);

        assertEquals("knowledge-search", manifest.name());
        assertEquals("检索知识库", manifest.description());
        assertEquals("1.0.0", manifest.version());
        assertEquals("java:com.example.Skill", manifest.entry());
        assertEquals(3000, manifest.timeoutMs());
        assertEquals(1, manifest.retry());
    }

    @Test
    void parse_shouldRejectInvalidName() throws Exception {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: Invalid_Name
                description: bad
                version: 1.0.0
                entry: java:com.example.Skill
                ---
                """);

        SkillManifest manifest = new SkillManifestParser().parse(skillMd);
        assertEquals("invalid-name", manifest.name());
    }

    @Test
    void parse_shouldRejectInvalidEntryPrefix() throws Exception {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: valid-name
                description: bad entry
                version: 1.0.0
                entry: http:com.example.Skill
                ---
                """);

        assertThrows(IllegalArgumentException.class, () -> new SkillManifestParser().parse(skillMd));
    }

    @Test
    void parse_shouldInferScriptEntryForAgentBrowserStyleManifest() throws Exception {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: Agent Browser
                description: browser skill
                allowed-tools: Bash(agent-browser:*)
                ---
                """);

        SkillManifest manifest = new SkillManifestParser().parse(skillMd);

        assertEquals("agent-browser", manifest.name());
        assertEquals("1.0.0", manifest.version());
        assertEquals("script:agent-browser", manifest.entry());
    }

    @Test
    void parse_shouldInferScriptEntryForClaudeAllowedToolsBlockList() throws Exception {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: Browser Skill
                description: browser skill
                allowed-tools:
                  - Bash(agent-browser:*)
                  - Bash(git:*)
                ---
                """);

        SkillManifest manifest = new SkillManifestParser().parse(skillMd);

        assertEquals("browser-skill", manifest.name());
        assertEquals("script:agent-browser", manifest.entry());
    }

    @Test
    void parse_shouldFallbackDescriptionToNameWhenMissing() throws Exception {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: Agent Browser
                allowed-tools: Bash(agent-browser:*)
                ---
                """);

        SkillManifest manifest = new SkillManifestParser().parse(skillMd);

        assertEquals("agent-browser", manifest.name());
        assertEquals("agent-browser", manifest.description());
        assertEquals("script:agent-browser", manifest.entry());
    }
}
