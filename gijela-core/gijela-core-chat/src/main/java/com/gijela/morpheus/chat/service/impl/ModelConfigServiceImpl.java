package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.domain.dto.model.ModelConfigSaveRequest;
import com.gijela.morpheus.chat.domain.entity.ChatModelConfig;
import com.gijela.morpheus.chat.domain.vo.model.ModelConfigItemResponse;
import com.gijela.morpheus.chat.domain.vo.model.ModelConfigOptionResponse;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import com.gijela.morpheus.chat.service.ModelConfigService;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleClient;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private static final String OPENAI_INTERFACE_STYLE = "openai";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModelConfigMapper mapper;
    private final OkHttpClient okHttpClient;

    public ModelConfigServiceImpl(ChatModelConfigMapper mapper, OkHttpClient okHttpClient) {
        this.mapper = mapper;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public List<ModelConfigItemResponse> list(String tenantId, String configType) {
        QueryWrapper<ChatModelConfig> query = new QueryWrapper<ChatModelConfig>()
                .eq("tenant_id", normalizeTenant(tenantId))
                .eq("deleted", 0)
                .orderByDesc("updated_at")
                .orderByDesc("id");
        String normalizedType = normalizeType(configType, false);
        if (normalizedType != null) {
            query.eq("config_type", normalizedType);
        }
        return mapper.selectList(query).stream().map(this::toItem).collect(Collectors.toList());
    }

    @Override
    public ModelConfigItemResponse create(String tenantId, String operator, ModelConfigSaveRequest request) {
        String resolvedTenant = normalizeTenant(tenantId);
        String configType = normalizeType(request.configType(), true);
        String providerKey = resolveInterfaceStyle();
        String model = normalizeRequired(request.model(), "model");
        String baseUrl = normalizeRequired(request.baseUrl(), "baseUrl");

        ChatModelConfig exists = mapper.selectOne(new QueryWrapper<ChatModelConfig>()
                .eq("tenant_id", resolvedTenant)
                .eq("config_type", configType)
                .eq("provider_key", providerKey)
                .eq("model", model)
                .eq("deleted", 0)
                .last("limit 1"));
        if (exists != null) {
            throw new BizException(ErrorCode.CONFLICT, "模型配置已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ChatModelConfig entity = new ChatModelConfig();
        entity.setTenantId(resolvedTenant);
        entity.setConfigType(configType);
        entity.setProviderKey(providerKey);
        entity.setModel(model);
        entity.setBaseUrl(baseUrl);
        entity.setApiKey(normalizeOptional(request.apiKey()));
        entity.setConnectTimeoutSeconds(resolveTimeout(request.connectTimeoutSeconds(), 10));
        entity.setReadTimeoutSeconds(resolveTimeout(request.readTimeoutSeconds(), 60));
        entity.setCallTimeoutSeconds(resolveTimeout(request.callTimeoutSeconds(), 120));
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setCreatedBy(normalizeOperator(operator));
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(normalizeOperator(operator));
        mapper.insert(entity);
        return toItem(entity);
    }

    @Override
    public ModelConfigItemResponse update(String tenantId, Long id, String operator, ModelConfigSaveRequest request) {
        ChatModelConfig entity = mapper.selectById(id);
        String resolvedTenant = normalizeTenant(tenantId);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        }

        String configType = normalizeType(request.configType(), true);
        String providerKey = resolveInterfaceStyle();
        String model = normalizeRequired(request.model(), "model");
        String baseUrl = normalizeRequired(request.baseUrl(), "baseUrl");

        ChatModelConfig duplicated = mapper.selectOne(new QueryWrapper<ChatModelConfig>()
                .eq("tenant_id", resolvedTenant)
                .eq("config_type", configType)
                .eq("provider_key", providerKey)
                .eq("model", model)
                .eq("deleted", 0)
                .ne("id", id)
                .last("limit 1"));
        if (duplicated != null) {
            throw new BizException(ErrorCode.CONFLICT, "模型配置已存在");
        }

        entity.setConfigType(configType);
        entity.setProviderKey(providerKey);
        entity.setModel(model);
        entity.setBaseUrl(baseUrl);
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            entity.setApiKey(null);
        } else if (request.apiKey() != null && !request.apiKey().isBlank()) {
            entity.setApiKey(request.apiKey().trim());
        }
        entity.setConnectTimeoutSeconds(resolveTimeout(request.connectTimeoutSeconds(), entity.getConnectTimeoutSeconds()));
        entity.setReadTimeoutSeconds(resolveTimeout(request.readTimeoutSeconds(), entity.getReadTimeoutSeconds()));
        entity.setCallTimeoutSeconds(resolveTimeout(request.callTimeoutSeconds(), entity.getCallTimeoutSeconds()));
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(normalizeOperator(operator));
        mapper.updateById(entity);
        return toItem(entity);
    }

    @Override
    public void delete(String tenantId, Long id) {
        ChatModelConfig entity = mapper.selectById(id);
        String resolvedTenant = normalizeTenant(tenantId);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        }
        entity.setDeleted(1);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Override
    public List<ModelConfigOptionResponse> listOptions(String tenantId, String configType) {
        String normalizedType = normalizeType(configType, true);
        return mapper.selectList(new QueryWrapper<ChatModelConfig>()
                        .eq("tenant_id", normalizeTenant(tenantId))
                        .eq("config_type", normalizedType)
                        .eq("enabled", 1)
                        .eq("deleted", 0)
                        .orderByAsc("provider_key")
                        .orderByAsc("model")
                        .orderByAsc("id"))
                .stream()
                .map(item -> new ModelConfigOptionResponse(
                        item.getConfigType(),
                        item.getProviderKey(),
                        item.getModel(),
                        item.getProviderKey() + " / " + item.getModel()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> testConnection(String tenantId, Long id) {
        ChatModelConfig entity = mapper.selectById(id);
        String resolvedTenant = normalizeTenant(tenantId);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        }
        if (entity.getEnabled() == null || entity.getEnabled() != 1) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "请先启用模型配置后再测试");
        }

        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("configType", entity.getConfigType());
        result.put("model", entity.getModel());
        result.put("baseUrl", entity.getBaseUrl());
        try {
            if ("EMBEDDING".equalsIgnoreCase(entity.getConfigType())) {
                testEmbedding(entity, result);
            } else {
                testChat(entity, result);
            }
            result.put("ok", true);
            result.put("status", "success");
            result.put("latencyMs", System.currentTimeMillis() - start);
            return result;
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("status", "failed");
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("message", ex.getMessage());
            return result;
        }
    }

    private ModelConfigItemResponse toItem(ChatModelConfig entity) {
        return new ModelConfigItemResponse(
                entity.getId(),
                entity.getConfigType(),
                entity.getProviderKey(),
                entity.getModel(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getCallTimeoutSeconds(),
                entity.getEnabled() != null && entity.getEnabled() == 1,
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "default";
        }
        return tenantId.trim();
    }

    private String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "unknown";
        }
        return operator.trim();
    }

    private String normalizeType(String configType, boolean required) {
        if (configType == null || configType.isBlank()) {
            if (required) {
                throw new BizException(ErrorCode.INVALID_ARGUMENT, "configType 不能为空");
            }
            return null;
        }
        String value = configType.trim().toUpperCase(Locale.ROOT);
        if (!"CHAT".equals(value) && !"EMBEDDING".equals(value)) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "configType 仅支持 CHAT 或 EMBEDDING");
        }
        return value;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, fieldName + " 不能为空");
        }
        return value.trim();
    }

    private String resolveInterfaceStyle() {
        return OPENAI_INTERFACE_STYLE;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Integer resolveTimeout(Integer candidate, Integer fallback) {
        if (candidate == null || candidate <= 0) {
            return fallback == null || fallback <= 0 ? 60 : fallback;
        }
        return candidate;
    }

    private void testChat(ChatModelConfig entity, Map<String, Object> result) {
        OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.getModel(),
                resolveTimeout(entity.getConnectTimeoutSeconds(), 10),
                resolveTimeout(entity.getReadTimeoutSeconds(), 60),
                resolveTimeout(entity.getCallTimeoutSeconds(), 120)
        );
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(okHttpClient, properties);
        ChatResponse response = client.chat(new ChatRequest(
                entity.getModel(),
                List.of(new ChatMessage("user", "请只回复：pong")),
                0.1,
                32,
                Map.of()
        ));
        result.put("preview", response == null ? null : response.content());
    }

    private void testEmbedding(ChatModelConfig entity, Map<String, Object> result) throws IOException {
        OkHttpClient runtimeClient = okHttpClient.newBuilder()
                .connectTimeout(resolveTimeout(entity.getConnectTimeoutSeconds(), 10), TimeUnit.SECONDS)
                .readTimeout(resolveTimeout(entity.getReadTimeoutSeconds(), 60), TimeUnit.SECONDS)
                .callTimeout(resolveTimeout(entity.getCallTimeoutSeconds(), 120), TimeUnit.SECONDS)
                .build();
        Map<String, Object> reqBody = Map.of(
                "model", entity.getModel(),
                "input", "test"
        );
        String embeddingUrl = buildEmbeddingUrl(entity.getBaseUrl());
        Request.Builder builder = new Request.Builder()
                .url(embeddingUrl)
                .post(RequestBody.create(OBJECT_MAPPER.writeValueAsString(reqBody), JSON));
        if (entity.getApiKey() != null && !entity.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + entity.getApiKey());
        }
        try (Response response = runtimeClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("embedding failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            Map<String, Object> root = OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});
            Object dataNode = root.get("data");
            if (!(dataNode instanceof List<?> dataList) || dataList.isEmpty()) {
                throw new IllegalStateException("embedding 响应 data 为空");
            }
            Object firstItem = dataList.get(0);
            if (!(firstItem instanceof Map<?, ?> itemMap)) {
                throw new IllegalStateException("embedding 响应格式异常");
            }
            Object embNode = itemMap.get("embedding");
            if (!(embNode instanceof List<?> embList)) {
                throw new IllegalStateException("embedding 响应缺少 embedding 字段");
            }
            result.put("dimension", embList.size());
        }
    }

    private String buildEmbeddingUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/embeddings")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "embeddings";
        }
        return trimmed + "/embeddings";
    }

    private String clip(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        String trimmed = responseBody.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
