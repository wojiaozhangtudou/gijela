package com.gijela.morpheus.chatflow.engine.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gijela.morpheus.chatflow.config.ChatFlowLlmProperties;
import com.gijela.morpheus.chatflow.domain.entity.LlmModelEntity;
import com.gijela.morpheus.chatflow.engine.NodeExecuteResult;
import com.gijela.morpheus.chatflow.engine.NodeExecutor;
import com.gijela.morpheus.chatflow.engine.RunContext;
import com.gijela.morpheus.chatflow.mapper.LlmModelMapper;
import com.gijela.morpheus.llm.sdk.core.error.LlmException;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.core.model.TokenUsage;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleClient;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleProperties;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmNodeExecutor implements NodeExecutor {

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");

    private final OkHttpClient okHttpClient;
    private final ChatFlowLlmProperties llmProperties;
    private final LlmModelMapper llmModelMapper;
    private final ObjectMapper objectMapper;

    public LlmNodeExecutor(OkHttpClient okHttpClient,
                           ChatFlowLlmProperties llmProperties,
                           LlmModelMapper llmModelMapper,
                           ObjectMapper objectMapper) {
        this.okHttpClient = okHttpClient;
        this.llmProperties = llmProperties;
        this.llmModelMapper = llmModelMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "llm";
    }

    @Override
    public NodeExecuteResult execute(RunContext context, JsonNode node, Map<String, Object> inputs) {
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        String mode = resolveLlmMode(node);
        String userPromptTemplate = readTextOrDefault(node, "userPromptTemplate", "{{sys.query}}");
        String systemPrompt = readTextOrDefault(node, "systemPrompt", "");
        String configuredModelKey = readRequiredText(node, "modelKey");
        ResolvedModel resolvedModel = resolveModel(configuredModelKey);
        if (!StringUtils.hasText(resolvedModel.targetModel)) {
            throw new IllegalArgumentException("LLM模型未配置 targetModel: " + configuredModelKey);
        }
        if (!StringUtils.hasText(resolvedModel.baseUrl)) {
            throw new IllegalArgumentException("LLM模型未配置 baseUrl: " + configuredModelKey);
        }
        if (!StringUtils.hasText(resolvedModel.apiKey)) {
            throw new IllegalArgumentException("LLM模型未配置 apiKey: " + configuredModelKey);
        }
        String model = resolvedModel.targetModel;
        double temperature = readDoubleOrDefault(node, "temperature", resolvedModel.defaultTemperature);
        int maxTokens = readIntOrDefault(node, "maxTokens", resolvedModel.defaultMaxTokens);
        String renderedPrompt = "chat".equals(mode)
            ? String.valueOf(safeInputs.get("sys.query") == null ? "" : safeInputs.get("sys.query")).trim()
            : renderTemplate(userPromptTemplate, safeInputs);

        List<ChatMessage> messages = buildMessages(systemPrompt, renderedPrompt, safeInputs);

        try {
            OpenAiCompatibleClient llmClient = new OpenAiCompatibleClient(
                okHttpClient,
                new OpenAiCompatibleProperties(
                    resolvedModel.baseUrl,
                    resolvedModel.apiKey,
                    model,
                    llmProperties.getConnectTimeoutSeconds(),
                    llmProperties.getReadTimeoutSeconds(),
                    llmProperties.getCallTimeoutSeconds()
                )
            );
            ChatRequest request = new ChatRequest(
                    model,
                    messages,
                    temperature,
                    maxTokens,
                    Map.of("requestId", context.getRunId())
            );
            ChatResponse response = CompletableFuture
                    .supplyAsync(() -> llmClient.chat(request))
                    .orTimeout(Math.max(5L, llmProperties.getCallTimeoutSeconds() + 5L), TimeUnit.SECONDS)
                    .join();

            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("text", response.content());
            outputs.put("finishReason", response.finishReason());
            outputs.put("usage", toUsageMap(response.usage()));
            outputs.put("prompt", renderedPrompt);
            return NodeExecuteResult.success(outputs);
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof LlmException llmEx) {
                return NodeExecuteResult.failed("LLM_CALL_FAILED: " + llmEx.getMessage());
            }
            if (cause != null) {
                return NodeExecuteResult.failed("LLM_CALL_FAILED: " + cause.getMessage());
            }
            return NodeExecuteResult.failed("LLM_CALL_FAILED: " + ex.getMessage());
        } catch (LlmException ex) {
            return NodeExecuteResult.failed("LLM_CALL_FAILED: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return NodeExecuteResult.failed("LLM_CALL_FAILED: " + ex.getMessage());
        }
    }

    private String resolveLlmMode(JsonNode node) {
        String rawMode = readText(node, "mode");
        if (!StringUtils.hasText(rawMode)) {
            rawMode = readText(node, "inputMode");
        }
        if (StringUtils.hasText(rawMode)) {
            String normalized = rawMode.trim().toLowerCase();
            if ("chat".equals(normalized) || normalized.contains("chat")) {
                return "chat";
            }
            if ("transform".equals(normalized) || normalized.contains("transform")) {
                return "transform";
            }
        }
        JsonNode mappedInputs = node == null || node.get("config") == null ? null : node.get("config").get("inputs");
        if (mappedInputs != null && mappedInputs.isArray() && !mappedInputs.isEmpty()) {
            return "transform";
        }
        return "chat";
    }

    private Map<String, Object> toUsageMap(TokenUsage usage) {
        if (usage == null) {
            return Map.of();
        }
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("promptTokens", usage.promptTokens());
        usageMap.put("completionTokens", usage.completionTokens());
        usageMap.put("totalTokens", usage.totalTokens());
        return usageMap;
    }

    private List<ChatMessage> buildMessages(String systemPrompt,
                                            String renderedPrompt,
                                            Map<String, Object> vars) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatMessage("system", systemPrompt));
        }

        for (ChatMessage historyMessage : parseHistoryMessages(vars.get("sys.chat_history"))) {
            messages.add(historyMessage);
        }

        String currentPrompt = String.valueOf(renderedPrompt == null ? "" : renderedPrompt).trim();
        if (!StringUtils.hasText(currentPrompt)) {
            Object query = vars.get("sys.query");
            currentPrompt = query == null ? "" : String.valueOf(query).trim();
        }
        messages.add(new ChatMessage("user", currentPrompt));
        return messages;
    }

    private List<ChatMessage> parseHistoryMessages(Object rawHistory) {
        if (rawHistory == null) {
            return List.of();
        }
        String historyText = String.valueOf(rawHistory).trim();
        if (!StringUtils.hasText(historyText)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(historyText, new TypeReference<List<Map<String, Object>>>() {
            });
            List<ChatMessage> messages = new ArrayList<>();
            List<Map<String, Object>> safeItems = items == null ? List.of() : items;
            for (Map<String, Object> item : safeItems) {
                if (item == null) {
                    continue;
                }
                String role = item.get("role") == null ? "" : String.valueOf(item.get("role")).trim();
                String content = item.get("content") == null ? "" : String.valueOf(item.get("content")).trim();
                if (!StringUtils.hasText(role) || !StringUtils.hasText(content)) {
                    continue;
                }
                if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) {
                    continue;
                }
                messages.add(new ChatMessage(role, content));
            }
            return messages;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String renderTemplate(String template, Map<String, Object> vars) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = vars.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String readTextOrDefault(JsonNode node, String key, String defaultValue) {
        if (node == null || node.get("config") == null || node.get("config").get(key) == null) {
            return defaultValue;
        }
        String value = node.get("config").get(key).asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String readText(JsonNode node, String key) {
        if (node == null || node.get("config") == null || node.get("config").get(key) == null) {
            return null;
        }
        String value = node.get("config").get(key).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String readRequiredText(JsonNode node, String key) {
        if (node == null || node.get("config") == null || node.get("config").get(key) == null) {
            throw new IllegalArgumentException("LLM节点缺少必填配置: " + key);
        }
        String value = node.get("config").get(key).asText();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("LLM节点缺少必填配置: " + key);
        }
        return value.trim();
    }

    private double readDoubleOrDefault(JsonNode node, String key, Double defaultValue) {
        if (defaultValue == null) {
            throw new IllegalArgumentException("LLM模型缺少默认配置: " + key);
        }
        if (node == null || node.get("config") == null || node.get("config").get(key) == null) {
            return defaultValue;
        }
        JsonNode raw = node.get("config").get(key);
        if (raw.isNumber()) {
            return raw.asDouble();
        }
        if (raw.isTextual()) {
            try {
                return Double.parseDouble(raw.asText());
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("LLM节点配置非法: " + key);
            }
        }
        throw new IllegalArgumentException("LLM节点配置非法: " + key);
    }

    private int readIntOrDefault(JsonNode node, String key, Integer defaultValue) {
        if (defaultValue == null) {
            throw new IllegalArgumentException("LLM模型缺少默认配置: " + key);
        }
        if (node == null || node.get("config") == null || node.get("config").get(key) == null) {
            return defaultValue;
        }
        JsonNode raw = node.get("config").get(key);
        if (raw.isInt() || raw.isLong()) {
            return raw.asInt();
        }
        if (raw.isTextual()) {
            try {
                return Integer.parseInt(raw.asText());
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("LLM节点配置非法: " + key);
            }
        }
        throw new IllegalArgumentException("LLM节点配置非法: " + key);
    }

    private ResolvedModel resolveModel(String modelKey) {
        LlmModelEntity modelEntity = llmModelMapper.selectOne(new LambdaQueryWrapper<LlmModelEntity>()
                .eq(LlmModelEntity::getDeleted, 0)
                .eq(LlmModelEntity::getEnabled, 1)
                .eq(LlmModelEntity::getModelKey, modelKey.trim())
                .last("limit 1"));
        if (modelEntity == null) {
            throw new IllegalArgumentException("未找到可用模型: " + modelKey);
        }
        return new ResolvedModel(
                modelEntity.getBaseUrl(),
                modelEntity.getApiKey(),
                modelEntity.getTargetModel(),
                modelEntity.getDefaultTemperature(),
                modelEntity.getDefaultMaxTokens()
        );
    }

    private static final class ResolvedModel {
        private final String baseUrl;
        private final String apiKey;
        private final String targetModel;
        private final Double defaultTemperature;
        private final Integer defaultMaxTokens;

        private ResolvedModel(String baseUrl, String apiKey, String targetModel, Double defaultTemperature, Integer defaultMaxTokens) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.targetModel = targetModel;
            this.defaultTemperature = defaultTemperature;
            this.defaultMaxTokens = defaultMaxTokens;
        }
    }
}
