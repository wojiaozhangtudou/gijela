package com.gijela.morpheus.llm.sdk.mcp.client;

public class McpClientException
extends RuntimeException {
    public McpClientException(String message) {
        super(message);
    }

    public McpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
