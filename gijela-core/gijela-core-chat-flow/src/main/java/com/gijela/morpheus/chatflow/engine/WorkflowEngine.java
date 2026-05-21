package com.gijela.morpheus.chatflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class WorkflowEngine {

    private final NodeExecutorRegistry registry;

    public WorkflowEngine(NodeExecutorRegistry registry) {
        this.registry = registry;
    }

    public WorkflowEngineResult executeB1(JsonNode definitionNode, RunContext context, Map<String, Object> requestInputs) {
        return executeB1(definitionNode, context, requestInputs, null);
    }

    public WorkflowEngineResult executeB1(JsonNode definitionNode,
                                          RunContext context,
                                          Map<String, Object> requestInputs,
                                          Consumer<NodeExecutionRecord> nodeCallback) {
        WorkflowEngineResult result = new WorkflowEngineResult();
        List<NodeExecutionRecord> records = new ArrayList<>();

        String startNodeId = findFirstNodeId(definitionNode, "start", "n_start");
        Set<String> visited = new HashSet<>();

        Map<String, Object> startInputs = requestInputs == null ? Map.of() : requestInputs;
        notifyNode(nodeCallback, createRunningRecord(startNodeId, "start", startInputs));
        NodeExecutionRecord startRecord = executeNode(startNodeId, "start", definitionNode, context, startInputs);
        records.add(startRecord);
        notifyNode(nodeCallback, startRecord);
        if (!"success".equals(startRecord.getStatus())) {
            result.setStatus("failed");
            result.setErrorMessage(startRecord.getErrorMessage());
            result.setNodeRecords(records);
            return result;
        }

        Map<String, Object> workflowInputs = extractStartInputs(context.getVariables().get(startNodeId));
        visited.add(startNodeId);

        String currentNodeId = startNodeId;
        while (true) {
            String nextNodeId = findNextNodeId(definitionNode, currentNodeId);
            if (nextNodeId == null || nextNodeId.isBlank()) {
                result.setStatus("failed");
                result.setErrorMessage("流程缺少从节点 " + currentNodeId + " 出发的连线");
                result.setNodeRecords(records);
                return result;
            }
            if (visited.contains(nextNodeId)) {
                result.setStatus("failed");
                result.setErrorMessage("检测到循环依赖，节点重复执行: " + nextNodeId);
                result.setNodeRecords(records);
                return result;
            }
            visited.add(nextNodeId);

            JsonNode nextNode = findNode(definitionNode, nextNodeId);
            String nextType = nextNode == null || nextNode.get("type") == null ? "unknown" : nextNode.get("type").asText();

            Map<String, Object> nodeInputs;
            if ("llm".equals(nextType)) {
                Object previousOut = context.getVariables().get(currentNodeId);
                nodeInputs = buildLlmInputs(nextNode, workflowInputs, previousOut);
            } else if ("end".equals(nextType)) {
                nodeInputs = buildEndInputs(context.getVariables().get(currentNodeId));
            } else {
                nodeInputs = Map.of();
            }

            notifyNode(nodeCallback, createRunningRecord(nextNodeId, nextType, nodeInputs));
            NodeExecutionRecord record = executeNode(nextNodeId, nextType, definitionNode, context, nodeInputs);
            records.add(record);
            notifyNode(nodeCallback, record);
            if (!"success".equals(record.getStatus())) {
                result.setStatus("failed");
                result.setErrorMessage(record.getErrorMessage());
                result.setNodeRecords(records);
                return result;
            }

            if ("end".equals(nextType)) {
                result.setNodeRecords(records);
                result.setStatus(record.getStatus());
                Object endOut = context.getVariables().get(nextNodeId);
                if (endOut instanceof Map<?, ?> map && map.get("finalResult") instanceof Map<?, ?> finalMap) {
                    Map<String, Object> finalResult = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : finalMap.entrySet()) {
                        if (e.getKey() != null) {
                            finalResult.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                    result.setFinalResult(finalResult);
                } else {
                    result.setFinalResult(Map.of());
                }
                return result;
            }

            currentNodeId = nextNodeId;
        }
    }

    private NodeExecutionRecord executeNode(String nodeId,
                                            String expectedType,
                                            JsonNode definitionNode,
                                            RunContext context,
                                            Map<String, Object> inputs) {
        JsonNode node = findNode(definitionNode, nodeId);
        String type = node == null || node.get("type") == null ? expectedType : node.get("type").asText();
        NodeExecutor executor = registry.get(type);

        LocalDateTime startedAt = LocalDateTime.now();
        NodeExecuteResult executeResult;
        try {
            executeResult = executor.execute(context, node, inputs);
        } catch (Exception ex) {
            executeResult = NodeExecuteResult.failed(ex.getMessage());
        }
        LocalDateTime endedAt = LocalDateTime.now();

        NodeExecutionRecord record = new NodeExecutionRecord();
        record.setNodeId(nodeId);
        record.setNodeType(type);
        record.setStatus(executeResult.getStatus());
        record.setStartedAt(startedAt);
        record.setEndedAt(endedAt);
        record.setDurationMs(Math.max(1L, Duration.between(startedAt, endedAt).toMillis()));
        record.setInputSnapshot(inputs == null ? Map.of() : inputs);
        record.setOutputSnapshot(executeResult.getOutputs());
        record.setErrorMessage(executeResult.getErrorMessage());

        if ("success".equals(executeResult.getStatus())) {
            context.getVariables().put(nodeId, executeResult.getOutputs() == null ? Map.of() : executeResult.getOutputs());
        }
        return record;
    }

    private JsonNode findNode(JsonNode definitionNode, String nodeId) {
        if (definitionNode == null || definitionNode.get("nodes") == null || !definitionNode.get("nodes").isArray()) {
            return null;
        }
        for (JsonNode node : definitionNode.get("nodes")) {
            if (node.get("id") != null && nodeId.equals(node.get("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private String findFirstNodeId(JsonNode definitionNode, String nodeType, String fallback) {
        if (definitionNode == null || definitionNode.get("nodes") == null || !definitionNode.get("nodes").isArray()) {
            return fallback;
        }
        for (JsonNode node : definitionNode.get("nodes")) {
            if (node.get("type") != null
                    && nodeType.equals(node.get("type").asText())
                    && node.get("id") != null
                    && !node.get("id").asText().isBlank()) {
                return node.get("id").asText();
            }
        }
        return fallback;
    }

    private Map<String, Object> buildLlmInputs(JsonNode llmNode,
                                               Map<String, Object> workflowInputs,
                                               Object previousOut) {
        Map<String, Object> safeWorkflowInputs = workflowInputs == null ? Map.of() : workflowInputs;
        Map<String, Object> llmInputs = new LinkedHashMap<>();

        String mode = resolveLlmMode(llmNode);

        if ("chat".equals(mode)) {
            llmInputs.put("sys.query", safeWorkflowInputs.get("sys.query"));
            llmInputs.put("sys.chat_history", safeWorkflowInputs.get("sys.chat_history"));
            llmInputs.put("sys.conversation_id", safeWorkflowInputs.get("sys.conversation_id"));
            return llmInputs;
        }

        JsonNode mappedInputs = llmNode == null || llmNode.get("config") == null
                ? null
                : llmNode.get("config").get("inputs");

        if (mappedInputs == null || !mappedInputs.isArray() || mappedInputs.isEmpty()) {
            throw new IllegalArgumentException("TRANSFORM 模式下 LLM 节点必须配置 inputs 入参映射");
        }

        for (JsonNode input : mappedInputs) {
            String name = input.get("name") == null ? "" : input.get("name").asText();
            if (name == null || name.isBlank()) {
                continue;
            }
            String source = input.get("source") == null ? "workflow" : input.get("source").asText("workflow");
            Object value;
            switch (source) {
                case "constant" -> value = readJsonValue(input.get("value"));
                case "previous" -> value = resolvePreviousValue(name, previousOut);
                case "workflow" -> value = safeWorkflowInputs.get(name);
                default -> value = safeWorkflowInputs.get(name);
            }
            llmInputs.put(name, value == null ? "" : value);
        }

        return llmInputs;
    }

    private String resolveLlmMode(JsonNode llmNode) {
        JsonNode config = llmNode == null ? null : llmNode.get("config");
        String rawMode = readText(config, "mode");
        if (rawMode == null || rawMode.isBlank()) {
            rawMode = readText(config, "inputMode");
        }

        if (rawMode != null) {
            String normalized = rawMode.trim().toLowerCase();
            if ("chat".equals(normalized) || normalized.contains("chat")) {
                return "chat";
            }
            if ("transform".equals(normalized) || normalized.contains("transform")) {
                return "transform";
            }
        }

        JsonNode mappedInputs = config == null ? null : config.get("inputs");
        if (mappedInputs != null && mappedInputs.isArray() && !mappedInputs.isEmpty()) {
            return "transform";
        }
        return "chat";
    }

    private String readText(JsonNode node, String key) {
        if (node == null || node.get(key) == null) {
            return null;
        }
        String value = node.get(key).asText();
        return value == null ? null : value.trim();
    }

    private Map<String, Object> extractStartInputs(Object startOut) {
        Map<String, Object> startInputs = new LinkedHashMap<>();
        if (!(startOut instanceof Map<?, ?> startMap) || !(startMap.get("inputs") instanceof Map<?, ?> inMap)) {
            return startInputs;
        }
        for (Map.Entry<?, ?> e : inMap.entrySet()) {
            if (e.getKey() != null) {
                startInputs.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return startInputs;
    }

    private Map<String, Object> buildEndInputs(Object previousOut) {
        Map<String, Object> endInputs = new LinkedHashMap<>();
        if (previousOut instanceof Map<?, ?> prevMap) {
            if (prevMap.get("text") != null) {
                endInputs.put("answer", prevMap.get("text"));
            } else if (prevMap.get("answer") != null) {
                endInputs.put("answer", prevMap.get("answer"));
            }
        }
        return endInputs;
    }

    private Object resolvePreviousValue(String key, Object previousOut) {
        if (!(previousOut instanceof Map<?, ?> prevMap)) {
            return "";
        }
        if (prevMap.containsKey(key)) {
            return prevMap.get(key);
        }
        if (prevMap.containsKey("text")) {
            return prevMap.get("text");
        }
        if (prevMap.containsKey("answer")) {
            return prevMap.get("answer");
        }
        return "";
    }

    private String findNextNodeId(JsonNode definitionNode, String sourceNodeId) {
        if (definitionNode == null || definitionNode.get("edges") == null || !definitionNode.get("edges").isArray()) {
            return null;
        }
        for (JsonNode edge : definitionNode.get("edges")) {
            String source = readEdgeNodeId(edge, "sourceNodeId", "source");
            String target = readEdgeNodeId(edge, "targetNodeId", "target");
            if (sourceNodeId.equals(source) && target != null && !target.isBlank()) {
                return target;
            }
        }
        return null;
    }

    private String readEdgeNodeId(JsonNode edge, String primary, String fallback) {
        if (edge == null) {
            return null;
        }
        JsonNode primaryNode = edge.get(primary);
        if (primaryNode != null && !primaryNode.asText().isBlank()) {
            return primaryNode.asText();
        }
        JsonNode fallbackNode = edge.get(fallback);
        if (fallbackNode != null && !fallbackNode.asText().isBlank()) {
            return fallbackNode.asText();
        }
        return null;
    }

    private Object readJsonValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.asDouble();
        }
        return node.toString();
    }

    private void notifyNode(Consumer<NodeExecutionRecord> nodeCallback, NodeExecutionRecord record) {
        if (nodeCallback != null && record != null) {
            nodeCallback.accept(record);
        }
    }

    private NodeExecutionRecord createRunningRecord(String nodeId, String nodeType, Map<String, Object> inputs) {
        NodeExecutionRecord record = new NodeExecutionRecord();
        record.setNodeId(nodeId);
        record.setNodeType(nodeType);
        record.setStatus("running");
        record.setStartedAt(LocalDateTime.now());
        record.setDurationMs(0L);
        record.setInputSnapshot(inputs == null ? Map.of() : inputs);
        record.setOutputSnapshot(Map.of());
        return record;
    }
}
