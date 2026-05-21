package com.gijela.morpheus.chat.adapter.skill;

import com.gijela.morpheus.chat.adapter.mcp.McpGateway;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillSchemaBuilder;

import java.util.Map;

public class KnowledgeSearchSkillProvider implements SkillProvider {

    private final McpGateway mcpGateway;

    public KnowledgeSearchSkillProvider(McpGateway mcpGateway) {
        this.mcpGateway = mcpGateway;
    }

    @Override
    public String name() {
        return "knowledge.search";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(),
                "查询知识库片段（当问题需要事实依据、部署步骤、配置细节时必须优先调用）",
                Map.of("query", Map.of("type", "string", "description", "检索关键词")),
                java.util.List.of("query"));
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        String tenantId = context == null || context.tenantId() == null || context.tenantId().isBlank()
                ? "default"
                : context.tenantId();
        Map<String, Object> result = mcpGateway.invoke(tenantId, call.name(), call.arguments());
        return new ToolResult(call.id(), true, result, null);
    }
}
