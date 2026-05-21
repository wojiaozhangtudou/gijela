package com.gijela.morpheus.llm.sdk.mcp.client;

import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;

public interface McpTransport {
    public String name();

    public McpSession open(McpEndpointConfig var1);
}
