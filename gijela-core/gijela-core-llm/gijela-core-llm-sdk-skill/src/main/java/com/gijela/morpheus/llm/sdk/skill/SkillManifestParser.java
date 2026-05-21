package com.gijela.morpheus.llm.sdk.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillManifestParser {

    private static final String NAME_PATTERN = "^[a-z0-9-]{3,64}$";
    private static final Pattern ALLOWED_TOOL_BIN_PATTERN = Pattern.compile("(?i)\\bbash\\(([^:()]+)(?::[^)]*)?\\)");
    private static final Pattern FRONTMATTER_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+\\s*:.*$");

    public SkillManifest parse(Path skillMdPath) throws IOException {
        List<String> lines = Files.readAllLines(skillMdPath, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
            throw new IllegalArgumentException("SKILL.md 缺少 YAML frontmatter 起始分隔符");
        }
        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if ("---".equals(lines.get(i).trim())) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            throw new IllegalArgumentException("SKILL.md 缺少 YAML frontmatter 结束分隔符");
        }

        Map<String, String> meta = parseFrontmatter(lines, end);

        String name = normalizeName(require(meta, "name"));
        String description = optional(meta, "description", name);
        String version = optional(meta, "version", "1.0.0");
        String entry = optional(meta, "entry", inferEntry(meta));
        validateName(name);
        validateEntry(entry);
        Integer timeoutMs = parseInt(meta.get("timeoutMs"));
        Integer retry = parseInt(meta.get("retry"));

        return new SkillManifest(name, description, version, entry, timeoutMs, retry, skillMdPath.toString());
    }

    private Map<String, String> parseFrontmatter(List<String> lines, int end) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (int i = 1; i < end; i++) {
            String line = lines.get(i).trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf(':');
            if (split <= 0 || line.startsWith("- ")) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();

            if (value.isBlank()) {
                List<String> blockValues = new ArrayList<>();
                int j = i + 1;
                while (j < end) {
                    String next = lines.get(j).trim();
                    if (next.isBlank() || next.startsWith("#")) {
                        j++;
                        continue;
                    }
                    if (FRONTMATTER_KEY_PATTERN.matcher(next).matches()) {
                        break;
                    }
                    if (next.startsWith("- ")) {
                        blockValues.add(next.substring(2).trim());
                        j++;
                        continue;
                    }
                    break;
                }
                if (!blockValues.isEmpty()) {
                    value = String.join(", ", blockValues);
                    i = j - 1;
                }
            }

            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            meta.put(key, value);
        }
        return meta;
    }

    private String require(Map<String, String> meta, String key) {
        String value = meta.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少必填字段: " + key);
        }
        return value;
    }

    private String optional(Map<String, String> meta, String key, String defaultValue) {
        String value = meta.get(key);
        if (value == null || value.isBlank()) {
            if (defaultValue == null || defaultValue.isBlank()) {
                throw new IllegalArgumentException("SKILL.md frontmatter 缺少必填字段: " + key);
            }
            return defaultValue;
        }
        return value;
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("frontmatter 数值字段非法: " + value, e);
        }
    }

    private void validateName(String name) {
        if (!name.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("frontmatter name 非法，需满足 [a-z0-9-]{3,64}: " + name);
        }
    }

    private void validateEntry(String entry) {
        if (!(entry.startsWith("java:") || entry.startsWith("script:"))) {
            throw new IllegalArgumentException("frontmatter entry 非法，需以 java: 或 script: 开头: " + entry);
        }
    }

    private String normalizeName(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return normalized;
    }

    private String inferEntry(Map<String, String> meta) {
        String allowedTools = meta.get("allowed-tools");
        if (allowedTools == null || allowedTools.isBlank()) {
            return null;
        }
        Matcher matcher = ALLOWED_TOOL_BIN_PATTERN.matcher(allowedTools);
        if (matcher.find()) {
            return "script:" + matcher.group(1).trim();
        }
        return null;
    }
}
