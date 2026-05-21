package com.gijela.morpheus.llm.sdk.core.event;

import com.gijela.morpheus.llm.sdk.core.model.TokenUsage;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

/**
 * 统一流式事件。
 */
public record LlmEvent(
        LlmEventType type,
        String textDelta,
        ToolCall toolCall,
        ToolResult toolResult,
        String errorMessage,
        TokenUsage usage
) {
}
