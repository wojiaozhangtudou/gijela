package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

import java.util.Map;

/**
 * 一个可执行技能的 SPI。一个 Provider 暴露：
 * <ul>
 *   <li>{@link #name()} 唯一名称（schema/工具名）</li>
 *   <li>{@link #schema()} OpenAI function-tool schema</li>
 *   <li>{@link #execute} 实际执行</li>
 * </ul>
 */
public interface SkillProvider {

    String name();

    Map<String, Object> schema();

    ToolResult execute(ToolCall call, ToolContext context);

    /** 人可读描述，默认从 schema.description 提取。 */
    default String description() {
        Map<String, Object> s = schema();
        if (s == null) return "";
        Object fn = s.get("function");
        if (fn instanceof Map<?, ?> fnMap) {
            Object desc = fnMap.get("description");
            if (desc != null) return String.valueOf(desc);
        }
        Object desc = s.get("description");
        return desc == null ? "" : String.valueOf(desc);
    }

    default String version() { return "1.0.0"; }

    default SkillSource source() { return SkillSource.BUILTIN; }

    /** 是否内置（不可被前端删除编辑）。默认：仅当 source=BUILTIN 时为 true。 */
    default boolean builtin() { return source() == SkillSource.BUILTIN; }
}
