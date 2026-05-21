package com.gijela.morpheus.llm.sdk.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.llm.sdk.core.event.LlmEvent;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventType;
import com.gijela.morpheus.llm.sdk.core.model.TokenUsage;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 事件解析器。
 */
public class SseEventParser {

    private static final String SSE_PREFIX = "data:";

    private final ObjectMapper objectMapper;
    private final Map<String, ToolCallAccumulator> toolCallAccumulators;
    private final Map<String, String> emittedToolCallFingerprints;
    private final Map<Integer, String> toolCallKeyByIndex;
    private TokenUsage latestUsage;
    private boolean started;
    private boolean done;

    public SseEventParser() {
        this(new ObjectMapper());
    }

    public SseEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.toolCallAccumulators = new LinkedHashMap<>();
        this.emittedToolCallFingerprints = new HashMap<>();
        this.toolCallKeyByIndex = new HashMap<>();
    }

    public List<LlmEvent> parseLine(String line) {
        if (done || line == null || line.isBlank() || !line.startsWith(SSE_PREFIX)) {
            return List.of();
        }

        String data = line.substring(SSE_PREFIX.length()).trim();
        if (data.isEmpty()) {
            return List.of();
        }

        if ("[DONE]".equals(data)) {
            return buildDoneEvent();
        }

        try {
            Map<String, Object> chunk = objectMapper.readValue(data, new TypeReference<>() {
            });
            return mapChunk(chunk);
        } catch (Exception e) {
            return List.of(new LlmEvent(LlmEventType.ERROR, null, null, null, "SSE 事件解析失败: " + e.getMessage(), null));
        }
    }

    private List<LlmEvent> mapChunk(Map<String, Object> chunk) {
        List<LlmEvent> events = new ArrayList<>();
        if (!started) {
            started = true;
            events.add(new LlmEvent(LlmEventType.START, null, null, null, null, null));
        }

        TokenUsage usage = parseUsage(chunk);
        if (usage != null) {
            latestUsage = usage;
        }

        Object choicesObj = chunk.get("choices");
        if (!(choicesObj instanceof List<?> choices)) {
            return events;
        }

        for (Object choiceObj : choices) {
            if (!(choiceObj instanceof Map<?, ?> choice)) {
                continue;
            }

            Object deltaObj = choice.get("delta");
            if (deltaObj instanceof Map<?, ?> delta) {
                String text = asText(delta.get("content"));
                if (text != null && !text.isBlank()) {
                    events.add(new LlmEvent(LlmEventType.DELTA, text, null, null, null, null));
                }

                Object toolCallsObj = delta.get("tool_calls");
                if (toolCallsObj instanceof List<?> toolCalls) {
                    collectToolCalls(toolCalls, events);
                }
            }

            String finishReason = asText(choice.get("finish_reason"));
            if ("tool_calls".equalsIgnoreCase(finishReason)) {
                // 只 flush 工具调用，done 事件等 [DONE] 标记到来后统一触发，
                // 以便 [DONE] 前的 usage-only chunk 能被正常解析
                events.addAll(flushPendingToolCalls());
                continue;
            }
            if ("stop".equalsIgnoreCase(finishReason)
                    || "length".equalsIgnoreCase(finishReason)) {
                // 只 flush 工具调用，done 事件等 [DONE] 标记到来后统一触发
                events.addAll(flushPendingToolCalls());
            }
        }

        return events;
    }

    private void collectToolCalls(List<?> toolCalls, List<LlmEvent> events) {
        for (Object toolCallObj : toolCalls) {
            if (!(toolCallObj instanceof Map<?, ?> toolCallMap)) {
                continue;
            }

            Integer index = asInteger(toolCallMap.get("index"));
            String id = asText(toolCallMap.get("id"));
            String key = resolveToolCallKey(index, id);
            ToolCallAccumulator accumulator = toolCallAccumulators.computeIfAbsent(key, k -> new ToolCallAccumulator());
            if (id != null && !id.isBlank()) {
                accumulator.id = id;
            }

            Object functionObj = toolCallMap.get("function");
            if (functionObj instanceof Map<?, ?> functionMap) {
                String name = asText(functionMap.get("name"));
                if (name != null && !name.isBlank()) {
                    accumulator.name = name;
                }

                String argumentFragment = asText(functionMap.get("arguments"));
                if (argumentFragment != null) {
                    accumulator.argumentsBuilder.append(argumentFragment);
                }
            }

            ToolCall toolCall = toToolCallIfReady(key, accumulator);
            if (toolCall != null) {
                events.add(new LlmEvent(LlmEventType.TOOL_CALL, null, toolCall, null, null, null));
            }
        }
    }

    private List<LlmEvent> flushPendingToolCalls() {
        List<LlmEvent> events = new ArrayList<>();
        for (Map.Entry<String, ToolCallAccumulator> entry : toolCallAccumulators.entrySet()) {
            ToolCall toolCall = toToolCallWithFallback(entry.getKey(), entry.getValue());
            if (toolCall == null) {
                continue;
            }
            String currentFingerprint = fingerprint(toolCall);
            String previous = emittedToolCallFingerprints.get(entry.getKey());
            if (!currentFingerprint.equals(previous)) {
                emittedToolCallFingerprints.put(entry.getKey(), currentFingerprint);
                events.add(new LlmEvent(LlmEventType.TOOL_CALL, null, toolCall, null, null, null));
            }
        }
        return events;
    }

    private ToolCall toToolCallIfReady(String key, ToolCallAccumulator accumulator) {
        if (accumulator.name == null || accumulator.name.isBlank()) {
            return null;
        }

        String argumentsText = accumulator.argumentsBuilder.toString();
        if (argumentsText.isBlank()) {
            ToolCall toolCall = new ToolCall(resolveToolCallId(key, accumulator), accumulator.name, Map.of());
            return markAndReturnIfChanged(key, toolCall);
        }

        Map<String, Object> arguments;
        try {
            arguments = objectMapper.readValue(argumentsText, new TypeReference<>() {
            });
        } catch (Exception e) {
            return null;
        }

        ToolCall toolCall = new ToolCall(resolveToolCallId(key, accumulator), accumulator.name, arguments);
        return markAndReturnIfChanged(key, toolCall);
    }

    private ToolCall toToolCallWithFallback(String key, ToolCallAccumulator accumulator) {
        if (accumulator.name == null || accumulator.name.isBlank()) {
            return null;
        }

        String argumentsText = accumulator.argumentsBuilder.toString();
        Map<String, Object> arguments = Map.of();
        if (!argumentsText.isBlank()) {
            try {
                arguments = objectMapper.readValue(argumentsText, new TypeReference<>() {
                });
            } catch (Exception e) {
                arguments = Map.of("_raw", argumentsText);
            }
        }
        return new ToolCall(resolveToolCallId(key, accumulator), accumulator.name, arguments);
    }

    private List<LlmEvent> buildDoneEvent() {
        if (done) {
            return List.of();
        }
        done = true;
        return List.of(new LlmEvent(LlmEventType.DONE, null, null, null, null, latestUsage));
    }

    private static TokenUsage parseUsage(Map<String, Object> chunk) {
        if (chunk == null) {
            return null;
        }
        Object usageObj = chunk.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            return null;
        }
        int prompt = intValue(usageMap.get("prompt_tokens"));
        int completion = intValue(usageMap.get("completion_tokens"));
        int total = intValue(usageMap.get("total_tokens"));
        if (prompt <= 0 && completion <= 0 && total <= 0) {
            return null;
        }
        return new TokenUsage(prompt, completion, total);
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String resolveToolCallKey(Integer index, String id) {
        if (id != null && !id.isBlank()) {
            String idKey = id;
            if (index != null) {
                String oldIndexKey = toolCallKeyByIndex.put(index, idKey);
                migrateAccumulatorIfNeeded(oldIndexKey, idKey);
            }
            return idKey;
        }
        if (index != null) {
            String known = toolCallKeyByIndex.get(index);
            if (known != null && !known.isBlank()) {
                return known;
            }
            return "tool-call-index-" + index;
        }
        return "tool-call-unknown";
    }

    private void migrateAccumulatorIfNeeded(String oldKey, String newKey) {
        if (oldKey == null || oldKey.isBlank() || oldKey.equals(newKey)) {
            return;
        }
        ToolCallAccumulator oldAccumulator = toolCallAccumulators.remove(oldKey);
        if (oldAccumulator == null) {
            return;
        }
        ToolCallAccumulator target = toolCallAccumulators.computeIfAbsent(newKey, k -> new ToolCallAccumulator());
        if ((target.id == null || target.id.isBlank()) && oldAccumulator.id != null && !oldAccumulator.id.isBlank()) {
            target.id = oldAccumulator.id;
        }
        if ((target.name == null || target.name.isBlank()) && oldAccumulator.name != null && !oldAccumulator.name.isBlank()) {
            target.name = oldAccumulator.name;
        }
        if (!oldAccumulator.argumentsBuilder.isEmpty()) {
            target.argumentsBuilder.insert(0, oldAccumulator.argumentsBuilder);
        }
    }

    private static String resolveToolCallId(String key, ToolCallAccumulator accumulator) {
        if (accumulator.id != null && !accumulator.id.isBlank()) {
            return accumulator.id;
        }
        return key;
    }

    private ToolCall markAndReturnIfChanged(String key, ToolCall toolCall) {
        String currentFingerprint = fingerprint(toolCall);
        String previous = emittedToolCallFingerprints.get(key);
        if (currentFingerprint.equals(previous)) {
            return null;
        }
        emittedToolCallFingerprints.put(key, currentFingerprint);
        return toolCall;
    }

    private static String fingerprint(ToolCall toolCall) {
        return toolCall.id() + "|" + toolCall.name() + "|" + toolCall.arguments();
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder argumentsBuilder = new StringBuilder();
    }
}
