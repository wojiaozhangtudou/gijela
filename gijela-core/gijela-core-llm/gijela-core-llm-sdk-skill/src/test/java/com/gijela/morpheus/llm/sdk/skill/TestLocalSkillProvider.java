package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

import java.util.Map;

public class TestLocalSkillProvider implements SkillProvider {

    @Override
    public String name() {
        return "dummy.local";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(), "dummy local provider", Map.of());
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        return new ToolResult(call.id(), true, Map.of("ok", true), null);
    }
}
