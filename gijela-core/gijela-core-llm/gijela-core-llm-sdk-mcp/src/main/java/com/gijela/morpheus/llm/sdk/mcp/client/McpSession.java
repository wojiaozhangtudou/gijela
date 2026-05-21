package com.gijela.morpheus.llm.sdk.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface McpSession
extends AutoCloseable {
    public JsonNode call(String var1, Map<String, Object> var2);

    @Override
    public void close();
}
