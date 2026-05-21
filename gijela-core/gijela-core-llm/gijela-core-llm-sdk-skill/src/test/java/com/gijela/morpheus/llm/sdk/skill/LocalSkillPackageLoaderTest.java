package com.gijela.morpheus.llm.sdk.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalSkillPackageLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void load_shouldScanSkillDirectories() throws Exception {
        Path skillDir = tempDir.resolve("dummy-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: dummy-skill
                description: dummy
                version: 1.0.0
                entry: java:com.example.Dummy
                ---
                """);

        List<SkillManifest> manifests = new LocalSkillPackageLoader().load(tempDir.toString());

        assertEquals(1, manifests.size());
        assertEquals("dummy-skill", manifests.get(0).name());
    }
}
