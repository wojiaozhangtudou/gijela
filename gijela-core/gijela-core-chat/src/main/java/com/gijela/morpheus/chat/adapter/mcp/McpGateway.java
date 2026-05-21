package com.gijela.morpheus.chat.adapter.mcp;

import java.util.Map;

public class McpGateway {

    private final KnowledgeSearchGateway knowledgeSearchGateway;

    public McpGateway(KnowledgeSearchGateway knowledgeSearchGateway) {
        this.knowledgeSearchGateway = knowledgeSearchGateway;
    }

    public Map<String, Object> invoke(String tenantId, String capability, Map<String, Object> arguments) {
        if ("knowledge.search".equals(capability)) {
            Object query = arguments == null ? null : arguments.get("query");
            return knowledgeSearchGateway.search(tenantId, query == null ? "" : String.valueOf(query));
        }
        return Map.of(
                "capability", capability,
                "supported", false,
                "message", "当前仅提供 knowledge.search mock"
        );
    }
}
