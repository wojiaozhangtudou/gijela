package com.gijela.morpheus.llm.sdk.core.client;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

/**
 * 工具执行器。
 */
public interface ToolExecutor {

    ToolResult execute(ToolCall call, ToolContext context);
}
