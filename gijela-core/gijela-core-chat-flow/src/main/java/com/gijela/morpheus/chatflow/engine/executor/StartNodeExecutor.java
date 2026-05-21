package com.gijela.morpheus.chatflow.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.gijela.morpheus.chatflow.engine.NodeExecuteResult;
import com.gijela.morpheus.chatflow.engine.NodeExecutor;
import com.gijela.morpheus.chatflow.engine.RunContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public String type() {
        return "start";
    }

    @Override
    public NodeExecuteResult execute(RunContext context, JsonNode node, Map<String, Object> inputs) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("inputs", inputs == null ? Map.of() : inputs);
        return NodeExecuteResult.success(outputs);
    }
}
