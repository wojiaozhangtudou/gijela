package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

import java.util.List;
import java.util.Map;

/** 零业务示例技能：回显输入。SDK 不自动注册，业务侧按需 register。 */
public class EchoSkillProvider implements SkillProvider {

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(), "回显输入内容", Map.of(
                "text", Map.of("type", "string", "description", "要回显的内容")
        ), List.of("text"));
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        Object text = call.arguments() == null ? null : call.arguments().get("text");
        return new ToolResult(call.id(), true, Map.of("text", text == null ? "" : String.valueOf(text)), null);
    }
}
