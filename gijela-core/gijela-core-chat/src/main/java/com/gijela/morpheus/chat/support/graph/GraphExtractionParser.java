package com.gijela.morpheus.chat.support.graph;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractEntityVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractRelationshipVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GraphExtractionParser {

    private final ChatModuleProperties properties;

    public GraphExtractionParser(ChatModuleProperties properties) {
        this.properties = properties;
    }

    public ParsedResult parse(String rawText, String tenantId, String graphSpace) {
        Map<String, GraphExtractEntityVO> entityMap = new LinkedHashMap<>();
        Set<String> relationshipKeys = new LinkedHashSet<>();
        List<GraphExtractRelationshipVO> relationships = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            warnings.add("未提取到可解析内容");
            return new ParsedResult(List.of(), List.of(), warnings);
        }
        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            String candidate = line == null ? "" : line.trim();
            if (candidate.isBlank() || !candidate.startsWith("(\"") || !candidate.endsWith(")")) {
                continue;
            }
            String body = candidate.substring(1, candidate.length() - 1);
            String[] parts = body.split("\\|");
            if (parts.length < 4) {
                continue;
            }
            String type = cleanToken(parts[0]);
            if ("entity".equalsIgnoreCase(type)) {
                if (parts.length < 4) {
                    continue;
                }
                String entityName = normalizeName(cleanToken(parts[1]));
                if (entityName.isBlank()) {
                    continue;
                }
                entityMap.put(entityName, new GraphExtractEntityVO(
                        entityName,
                        entityName,
                        cleanToken(parts[2]),
                        cleanToken(parts[3])
                ));
                if (entityMap.size() >= properties.getGraph().getMaxPreviewEntities()) {
                    warnings.add("实体预览已截断到上限 " + properties.getGraph().getMaxPreviewEntities());
                    break;
                }
            }
        }
        for (String line : lines) {
            String candidate = line == null ? "" : line.trim();
            if (candidate.isBlank() || !candidate.startsWith("(\"") || !candidate.endsWith(")")) {
                continue;
            }
            String body = candidate.substring(1, candidate.length() - 1);
            String[] parts = body.split("\\|");
            if (parts.length < 5) {
                continue;
            }
            String type = cleanToken(parts[0]);
            if (!"relationship".equalsIgnoreCase(type)) {
                continue;
            }
            String source = normalizeName(cleanToken(parts[1]));
            String target = normalizeName(cleanToken(parts[2]));
            if (source.isBlank() || target.isBlank() || !entityMap.containsKey(source) || !entityMap.containsKey(target) || source.equals(target)) {
                continue;
            }
            String description = cleanToken(parts[3]);
            int strength = parseStrength(parts[4]);
            String relationshipId = buildRelationshipId(tenantId, graphSpace, source, target, description);
            if (!relationshipKeys.add(relationshipId)) {
                continue;
            }
            relationships.add(new GraphExtractRelationshipVO(source, target, description, strength, relationshipId));
            if (relationships.size() >= properties.getGraph().getMaxPreviewRelationships()) {
                warnings.add("关系预览已截断到上限 " + properties.getGraph().getMaxPreviewRelationships());
                break;
            }
        }
        if (entityMap.isEmpty() && relationships.isEmpty()) {
            warnings.add("未识别到符合约定格式的实体或关系，请检查抽取结果格式");
        }
        return new ParsedResult(new ArrayList<>(entityMap.values()), relationships, warnings);
    }

    private String cleanToken(String token) {
        String value = token == null ? "" : token.trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.trim();
    }

    private String normalizeName(String token) {
        return cleanToken(token).toUpperCase(Locale.ROOT);
    }

    private int parseStrength(String token) {
        try {
            int value = Integer.parseInt(cleanToken(token));
            if (value < 1) {
                return 1;
            }
            return Math.min(value, 10);
        } catch (Exception ex) {
            return 5;
        }
    }

    private String buildRelationshipId(String tenantId, String graphSpace, String source, String target, String description) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.join("|", tenantId, graphSpace, source, target, description)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            return tenantId + "-" + graphSpace + "-" + source + "-" + target;
        }
    }

    public record ParsedResult(
            List<GraphExtractEntityVO> entities,
            List<GraphExtractRelationshipVO> relationships,
            List<String> warnings
    ) {
    }
}