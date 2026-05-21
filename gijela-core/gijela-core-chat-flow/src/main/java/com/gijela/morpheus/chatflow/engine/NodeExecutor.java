package com.gijela.morpheus.chatflow.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface NodeExecutor {

    String type();

    NodeExecuteResult execute(RunContext context, JsonNode node, Map<String, Object> inputs);
}
