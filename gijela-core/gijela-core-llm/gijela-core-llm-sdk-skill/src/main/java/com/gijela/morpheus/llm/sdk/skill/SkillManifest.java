package com.gijela.morpheus.llm.sdk.skill;

public record SkillManifest(
        String name,
        String description,
        String version,
        String entry,
        Integer timeoutMs,
        Integer retry,
        String sourcePath
) {
}
