package com.gijela.morpheus.llm.sdk.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LocalSkillPackageLoader {

    private static final Logger logger = LoggerFactory.getLogger(LocalSkillPackageLoader.class);

    private final SkillManifestParser parser = new SkillManifestParser();

    public List<SkillManifest> load(String localPath) {
        List<SkillManifest> manifests = new ArrayList<>();
        if (localPath == null || localPath.isBlank()) {
            return manifests;
        }

        Path root = Paths.get(localPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            logger.info("[skills] 本地 skills 目录不存在，跳过扫描: {}", localPath);
            return manifests;
        }

        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(skillDir -> {
                Path skillMd = skillDir.resolve("SKILL.md");
                if (!Files.exists(skillMd)) {
                    return;
                }
                try {
                    SkillManifest manifest = parser.parse(skillMd);
                    manifests.add(manifest);
                } catch (Exception e) {
                    logger.warn("[skills] 解析 SKILL.md 失败, path={}, err={}", skillMd, e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("[skills] 扫描本地 skills 目录失败, path={}, err={}", localPath, e.getMessage());
        }
        return manifests;
    }
}
