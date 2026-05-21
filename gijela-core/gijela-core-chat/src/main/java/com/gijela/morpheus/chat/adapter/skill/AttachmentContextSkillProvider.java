package com.gijela.morpheus.chat.adapter.skill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillSchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AttachmentContextSkillProvider implements SkillProvider {

    private final ChatAttachmentMapper chatAttachmentMapper;

    public AttachmentContextSkillProvider(ChatAttachmentMapper chatAttachmentMapper) {
        this.chatAttachmentMapper = chatAttachmentMapper;
    }

    @Override
    public String name() {
        return "attachment.context";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(),
                "读取当前会话附件上下文（当用户提到附件/文档/上传文件时优先调用）",
                Map.of(
                        "query", Map.of("type", "string", "description", "按文件名或摘要关键词筛选（可选）"),
                        "limit", Map.of("type", "integer", "description", "最多返回条数，默认5，最大20"),
                        "includeRaw", Map.of("type", "boolean", "description", "是否包含原文片段，默认false")
                ));
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        return new ToolResult(call.id(), true, loadAttachmentContext(call.arguments(), context), null);
    }

    private Map<String, Object> loadAttachmentContext(Map<String, Object> arguments, ToolContext context) {
        String tenantId = context == null || context.tenantId() == null || context.tenantId().isBlank()
                ? "default"
                : context.tenantId();
        String sessionId = extractSessionId(context);
        if (sessionId == null || sessionId.isBlank()) {
            return Map.of(
                    "sessionId", null,
                    "count", 0,
                    "items", List.of(),
                    "contextText", "当前无会话，无法读取附件上下文"
            );
        }

        String query = argString(arguments, "query");
        int limit = argInt(arguments, "limit", 5, 1, 20);
        boolean includeRaw = argBool(arguments, "includeRaw", false);

        QueryWrapper<ChatAttachment> wrapper = new QueryWrapper<ChatAttachment>()
                .eq("tenant_id", tenantId)
                .eq("session_id", sessionId)
                .eq("process_status", 2)
                .orderByDesc("id")
                .last("LIMIT " + limit);
        if (query != null && !query.isBlank()) {
            String q = query.trim();
            wrapper.and(w -> w.like("file_name", q)
                    .or().like("summary", q)
                    .or().like("condensed_md", q));
        }

        List<ChatAttachment> rows = chatAttachmentMapper.selectList(wrapper);
        if ((rows == null || rows.isEmpty()) && query != null && !query.isBlank()) {
            QueryWrapper<ChatAttachment> fallbackWrapper = new QueryWrapper<ChatAttachment>()
                .eq("tenant_id", tenantId)
                .eq("session_id", sessionId)
                .eq("process_status", 2)
                .orderByDesc("id")
                .last("LIMIT " + limit);
            rows = chatAttachmentMapper.selectList(fallbackWrapper);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        StringBuilder contextText = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            ChatAttachment row = rows.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("fileName", row.getFileName());
            item.put("summary", row.getSummary());
            item.put("condensedMd", clip(row.getCondensedMd(), 2000));
            if (includeRaw) {
                item.put("rawText", clip(row.getRawText(), 2000));
            }
            items.add(item);

            contextText.append("[附件").append(i + 1).append("] ")
                    .append(row.getFileName() == null ? "未命名文件" : row.getFileName())
                    .append("\n摘要：")
                    .append(row.getSummary() == null || row.getSummary().isBlank() ? "-" : row.getSummary())
                    .append("\n浓缩内容：\n")
                    .append(clip(row.getCondensedMd(), 1200))
                    .append("\n\n");
            if (includeRaw) {
                contextText.append("原文片段：\n")
                        .append(clip(row.getRawText(), 1200))
                        .append("\n\n");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("count", items.size());
        result.put("items", items);
        result.put("contextText", contextText.toString());
        return result;
    }

    private String extractSessionId(ToolContext context) {
        if (context == null || context.attributes() == null) {
            return null;
        }
        Object sessionId = context.attributes().get("sessionId");
        return sessionId == null ? null : String.valueOf(sessionId);
    }

    private String argString(Map<String, Object> args, String key) {
        if (args == null || key == null) {
            return null;
        }
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int argInt(Map<String, Object> args, String key, int defaultValue, int min, int max) {
        if (args == null || key == null) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed < min) {
                return min;
            }
            return Math.min(parsed, max);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private boolean argBool(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null || key == null) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String clip(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}
