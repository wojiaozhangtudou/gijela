package com.gijela.morpheus.chat.adapter.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpGatewayTest {

    @Test
    void invoke_shouldReturnUnsupportedForUnknownCapability() {
        com.gijela.morpheus.chat.mapper.ChatModelConfigMapper mapper =
            org.mockito.Mockito.mock(com.gijela.morpheus.chat.mapper.ChatModelConfigMapper.class);
        KnowledgeSearchGateway gateway = new KnowledgeSearchGateway(
            new com.gijela.morpheus.chat.config.ChatModuleProperties(),
            new okhttp3.OkHttpClient(),
            mapper);
        McpGateway mcpGateway = new McpGateway(gateway);

        Map<String, Object> result = mcpGateway.invoke("tenant-a", "unknown.capability", Map.of());

        assertEquals("unknown.capability", result.get("capability"));
        assertFalse((Boolean) result.get("supported"));
    }
}
