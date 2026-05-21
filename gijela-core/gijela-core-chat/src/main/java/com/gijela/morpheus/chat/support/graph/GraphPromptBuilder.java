package com.gijela.morpheus.chat.support.graph;

import com.gijela.morpheus.chat.config.ChatModuleProperties;

public class GraphPromptBuilder {

    private final ChatModuleProperties properties;

    public GraphPromptBuilder(ChatModuleProperties properties) {
        this.properties = properties;
    }

    public String buildPrompt(String graphSpace,
                              String title,
                              String extractMode,
                              String promptOverride,
                              String rawText) {
        if (promptOverride != null && !promptOverride.isBlank()) {
            return promptOverride.replace("{input_text}", rawText == null ? "" : rawText);
        }
        String mode = extractMode == null || extractMode.isBlank() ? "LLM_RULES" : extractMode.trim();
        StringBuilder builder = new StringBuilder();
        builder.append("你是知识图谱抽取助手。请从输入文本中抽取实体和关系，只允许输出约定行格式。\n");
        builder.append("图谱空间：").append(graphSpace).append("\n");
        if (title != null && !title.isBlank()) {
            builder.append("文档标题：").append(title.trim()).append("\n");
        }
        builder.append("抽取模式：").append(mode).append("\n\n");
        builder.append("输出格式：\n");
        builder.append("1) 实体：(\"entity\"|实体名|类型|描述)\n");
        builder.append("2) 关系：(\"relationship\"|源实体|目标实体|关系描述|强度)\n");
        builder.append("3) 若无结果，输出：(\"empty\"|NONE|NONE|NONE)\n\n");
        builder.append("硬性规则：\n");
        builder.append("- 实体名统一大写；\n");
        builder.append("- 类型仅允许：人物|组织|地点|事件|时间|概念|产品|其他；\n");
        builder.append("- 强度必须是 1 到 10 的整数；\n");
        builder.append("- 禁止输出 JSON、Markdown、标题、解释说明；\n");
        builder.append("- 每行仅输出一条实体或关系；\n");
        if (!"LLM_ONLY".equalsIgnoreCase(mode)) {
            builder.append("- 关系两端实体必须已在实体列表中；\n");
            builder.append("- 删除自环和重复关系；\n");
            builder.append("- 尽量合并语义相同的重复实体。\n");
        }
        builder.append("\n输入文本：\n");
        builder.append(rawText == null ? "" : truncate(rawText, properties.getGraph().getMaxTextChars()));
        return builder.toString();
    }

    private String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit);
    }
}