package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

import java.time.OffsetDateTime;
import java.util.Map;

/** 零业务示例技能：返回当前时间。SDK 不自动注册，业务侧按需 register。 */
public class TimeNowSkillProvider implements SkillProvider {

    @Override
    public String name() {
        return "time.now";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(), "返回当前时间", Map.of());
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        return new ToolResult(call.id(), true, Map.of("now", OffsetDateTime.now().toString()), null);
    }
}
