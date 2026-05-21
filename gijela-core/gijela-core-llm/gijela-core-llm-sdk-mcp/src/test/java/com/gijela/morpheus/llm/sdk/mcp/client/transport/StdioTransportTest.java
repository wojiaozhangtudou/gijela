package com.gijela.morpheus.llm.sdk.mcp.client.transport;

import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;
import com.gijela.morpheus.llm.sdk.mcp.client.transport.StdioTransport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class StdioTransportTest {
    StdioTransportTest() {
    }

    @Test
    void disabled_shouldThrow() {
        StdioTransport t = new StdioTransport(false, List.of(), 1L, 1L);
        Assertions.assertThrows(McpClientException.class, () -> t.open(McpEndpointConfig.stdio((String)"echo", List.of(), Map.of(), null)));
    }

    @Test
    void blankCommand_shouldThrow() {
        StdioTransport t = new StdioTransport(true, List.of(), 1L, 1L);
        Assertions.assertThrows(McpClientException.class, () -> t.open(McpEndpointConfig.stdio((String)"", List.of(), Map.of(), null)));
    }

    @Test
    void notInWhitelist_shouldThrow() {
        StdioTransport t = new StdioTransport(true, List.of("python"), 1L, 1L);
        Assertions.assertThrows(McpClientException.class, () -> t.open(McpEndpointConfig.stdio((String)"rm", List.of(), Map.of(), null)));
    }

    @Test
    @EnabledOnOs(value={OS.WINDOWS})
    void windowsCmdEcho_shouldStartAndImmediateExit() {
        StdioTransport t = new StdioTransport(true, List.of(), 1L, 1L);
        McpClientException ex = (McpClientException)Assertions.assertThrows(McpClientException.class, () -> t.open(McpEndpointConfig.stdio((String)"cmd", List.of("/c", "exit", "0"), Map.of(), null)));
        Assertions.assertNotNull((Object)ex.getMessage());
    }

    @Test
    @EnabledOnOs(value={OS.WINDOWS})
    void windowsLongRunningProcess_shouldOpenAndClose() {
        StdioTransport t = new StdioTransport(true, List.of(), 1L, 1L);
        try (McpSession session = t.open(McpEndpointConfig.stdio((String)"cmd", List.of("/c", "more"), Map.of(), null));){
            Assertions.assertEquals((Object)McpSession.class.isInstance(session), (Object)true);
        }
    }

    @Test
    void name_shouldBeStdio() {
        Assertions.assertEquals((Object)"stdio", (Object)new StdioTransport(false, List.of(), 1L, 1L).name());
    }
}
