package com.gijela.morpheus.llm.sdk.mcp.client;

import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;
import com.gijela.morpheus.llm.sdk.mcp.client.McpTransport;
import java.util.Locale;
import java.util.Map;

public class McpClientFactory {
    private final Map<String, McpTransport> transports;

    public McpClientFactory(Map<String, McpTransport> transports) {
        this.transports = Map.copyOf(transports);
    }

    public McpSession open(McpEndpointConfig config) {
        McpTransport t;
        if (config == null || config.transport() == null) {
            throw new McpClientException("MCP transport \u672a\u6307\u5b9a");
        }
        String key = config.transport().toLowerCase(Locale.ROOT);
        if ("http".equals(key)) {
            key = "streamable_http";
        }
        if ((t = this.transports.get(key)) == null) {
            throw new McpClientException("\u4e0d\u652f\u6301\u7684 MCP transport: " + config.transport());
        }
        return t.open(config);
    }

    public boolean supports(String transport) {
        if (transport == null) {
            return false;
        }
        String k = transport.toLowerCase(Locale.ROOT);
        if ("http".equals(k)) {
            k = "streamable_http";
        }
        return this.transports.containsKey(k);
    }
}
