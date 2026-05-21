package com.gijela.morpheus.chat.adapter.mcp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.entity.ChatModelConfig;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识检索网关：先调 embedding 模型将 query 向量化，再调 Qdrant /points/search 做向量相似度检索。
 */
public class KnowledgeSearchGateway {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ChatModuleProperties properties;
    private final OkHttpClient okHttpClient;
    private final ChatModelConfigMapper modelConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeSearchGateway(ChatModuleProperties properties,
                                  OkHttpClient okHttpClient,
                                  ChatModelConfigMapper modelConfigMapper) {
        this.properties = properties;
        this.okHttpClient = okHttpClient;
        this.modelConfigMapper = modelConfigMapper;
    }

    public Map<String, Object> listCollections() {
        String endpoint = buildQdrantCollectionsUrl();
        Request.Builder requestBuilder = new Request.Builder().url(endpoint).get();
        withQdrantAuth(requestBuilder);
        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("qdrant list collections failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            List<String> collections = mapCollections(responseBody);
            return Map.of(
                    "collections", collections,
                    "count", collections.size(),
                    "provider", "qdrant"
            );
        } catch (IOException e) {
            throw new RuntimeException("qdrant list collections request failed", e);
        }
    }

    public Map<String, Object> deleteCollection(String collection) {
        String resolvedCollection = resolveCollection(collection);
        String endpoint = buildQdrantCollectionUrl(resolvedCollection);
        Request.Builder requestBuilder = new Request.Builder().url(endpoint).delete();
        withQdrantAuth(requestBuilder);
        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("qdrant delete collection failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            return Map.of(
                    "collection", resolvedCollection,
                    "status", "deleted",
                    "provider", "qdrant"
            );
        } catch (IOException e) {
            throw new RuntimeException("qdrant delete collection request failed", e);
        }
    }

    public Map<String, Object> deleteVectors(String collection, List<String> ids) {
        String resolvedCollection = resolveCollection(collection);
        List<Object> normalizedIds = normalizeVectorIds(ids);
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        int deleted = deleteByIds(resolvedCollection, normalizedIds);
        return Map.of(
                "collection", resolvedCollection,
                "deleted", deleted,
                "status", "deleted",
                "provider", "qdrant"
        );
    }

    public Map<String, Object> clearVectors(String collection) {
        String resolvedCollection = resolveCollection(collection);
        int totalDeleted = 0;
        Object offset = null;

        while (true) {
            Map<String, Object> scrollBody = new LinkedHashMap<>();
            scrollBody.put("limit", 256);
            scrollBody.put("with_payload", false);
            scrollBody.put("with_vector", false);
            if (offset != null) {
                scrollBody.put("offset", offset);
            }

            String scrollEndpoint = buildQdrantPointsScrollUrl(resolvedCollection);
            Request.Builder scrollBuilder = new Request.Builder()
                    .url(scrollEndpoint)
                    .post(RequestBody.create(writeJson(scrollBody), JSON));
            withQdrantAuth(scrollBuilder);

            List<Object> currentIds;
            Object nextOffset;
            try (Response response = okHttpClient.newCall(scrollBuilder.build()).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("qdrant scroll points failed, status=" + response.code() + ", body=" + clip(responseBody));
                }
                Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
                Object resultNode = root.get("result");
                if (!(resultNode instanceof Map<?, ?> resultMap)) {
                    break;
                }
                currentIds = extractPointIds(resultMap.get("points"));
                nextOffset = resultMap.get("next_page_offset");
            } catch (IOException e) {
                throw new RuntimeException("qdrant scroll points request failed", e);
            }

            if (currentIds.isEmpty()) {
                break;
            }

            totalDeleted += deleteByIds(resolvedCollection, currentIds);
            if (nextOffset == null) {
                break;
            }
            offset = nextOffset;
        }

        return Map.of(
                "collection", resolvedCollection,
                "deleted", totalDeleted,
                "status", "cleared",
                "provider", "qdrant"
        );
    }

    public Map<String, Object> search(String tenantId, String query) {
        if (!properties.getRetrieval().isEnabled()) {
            return Map.of(
                    "collection", properties.getRetrieval().getCollection(),
                    "tenantId", tenantId,
                    "query", query,
                    "hits", List.of(),
                    "disabled", true
            );
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return Map.of(
                    "collection", properties.getRetrieval().getCollection(),
                    "tenantId", tenantId,
                    "query", safeQuery,
                    "hits", List.of()
            );
        }

        // Step 1: 调 embedding 模型将 query 向量化
        List<Double> vector = embedQuery(normalizeTenant(tenantId), safeQuery, null);

        // Step 2: 用向量调 Qdrant /points/search 做相似度检索
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", Math.max(1, properties.getRetrieval().getTopK()));
        body.put("with_payload", true);
        body.put("filter", Map.of(
                "must", List.of(
                        Map.of(
                                "key", properties.getRetrieval().getTenantField(),
                                "match", Map.of("value", normalizeTenant(tenantId))
                        )
                )
        ));

        String endpoint = buildQdrantSearchUrl();
        Request.Builder requestBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(writeJson(body), JSON));
        String apiKey = properties.getRetrieval().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("api-key", apiKey);
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("qdrant search failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            List<Map<String, Object>> hits = mapSearchHits(responseBody);
            return Map.of(
                    "collection", properties.getRetrieval().getCollection(),
                    "tenantId", normalizeTenant(tenantId),
                    "query", safeQuery,
                    "hits", hits,
                    "provider", "qdrant"
            );
        } catch (IOException e) {
            throw new RuntimeException("qdrant search request failed", e);
        }
    }

    public Map<String, Object> indexText(String tenantId, String title, String content, Integer chunkSize, Integer chunkOverlap) {
        return indexText(tenantId, title, content, chunkSize, chunkOverlap, null);
    }

    public Map<String, Object> indexText(String tenantId, String title, String content, Integer chunkSize, Integer chunkOverlap, String embeddingModel) {
        if (!properties.getRetrieval().isEnabled()) {
            return Map.of(
                    "collection", properties.getRetrieval().getCollection(),
                    "tenantId", normalizeTenant(tenantId),
                    "disabled", true,
                    "chunks", 0
            );
        }

        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        String normalizedTenant = normalizeTenant(tenantId);
        int effectiveChunkSize = resolveChunkSize(chunkSize);
        int effectiveChunkOverlap = resolveChunkOverlap(chunkOverlap, effectiveChunkSize);
        List<String> chunks = splitIntoChunks(normalizedContent, effectiveChunkSize, effectiveChunkOverlap);
        if (chunks.isEmpty()) {
            return Map.of(
                    "collection", properties.getRetrieval().getCollection(),
                    "tenantId", normalizedTenant,
                    "title", title == null ? "" : title,
                    "chunks", 0,
                    "provider", "qdrant",
                    "status", "indexed"
            );
        }

        List<Map<String, Object>> points = new ArrayList<>();
        long baseId = System.currentTimeMillis() * 1000000; // 使用时间戳生成基础 ID
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> vector = embedQuery(normalizedTenant, chunk, embeddingModel);
            if (i == 0 && properties.getRetrieval().isAutoCreateCollection()) {
                ensureCollectionExists(vector.size(), null);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(properties.getRetrieval().getTenantField(), normalizedTenant);
            payload.put(properties.getRetrieval().getContentField(), chunk);
            payload.put("title", title == null ? "" : title);
            payload.put("chunk_index", i);
            payload.put("source", "manual");

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", baseId + i);  // 数字 ID，而不是字符串 UUID
            point.put("vector", vector);
            point.put("payload", payload);
            points.add(point);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", points);
        body.put("wait", true);

        String endpoint = buildQdrantUpsertUrl();
        Request.Builder requestBuilder = new Request.Builder()
                .url(endpoint)
            .put(RequestBody.create(writeJson(body), JSON));
        String apiKey = properties.getRetrieval().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("api-key", apiKey);
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("qdrant upsert failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("collection", properties.getRetrieval().getCollection());
            result.put("tenantId", normalizedTenant);
            result.put("title", title == null ? "" : title);
            result.put("chunks", chunks.size());
            result.put("provider", "qdrant");
            result.put("status", "indexed");
            return result;
        } catch (IOException e) {
            throw new RuntimeException("qdrant upsert request failed", e);
        }
    }

    public Map<String, Object> initCollection(Integer vectorSize, String distance) {
        return initCollection("default", vectorSize, distance, null);
    }

    public Map<String, Object> initCollection(Integer vectorSize, String distance, String embeddingModel) {
        return initCollection("default", vectorSize, distance, embeddingModel);
    }

    public Map<String, Object> initCollection(String tenantId, Integer vectorSize, String distance, String embeddingModel) {
        String resolvedTenant = normalizeTenant(tenantId);
        int effectiveVectorSize = vectorSize == null ? 0 : vectorSize;
        String effectiveDistance = distance == null || distance.isBlank()
                ? properties.getRetrieval().getVectorDistance()
                : distance;
        boolean autoDetected = false;
        String autoDetectedModel = null;
        if (effectiveVectorSize <= 0) {
            Map<String, Object> detected = detectEmbeddingDimension(resolvedTenant, "collection init dimension probe", embeddingModel);
            Object dimension = detected.get("dimension");
            if (!(dimension instanceof Number number) || number.intValue() <= 0) {
                throw new IllegalStateException("自动探测 embedding 维度失败");
            }
            effectiveVectorSize = number.intValue();
            autoDetectedModel = Objects.toString(detected.get("model"), "");
            autoDetected = true;
        }

        Map<String, Object> result = ensureCollectionExists(effectiveVectorSize, effectiveDistance);
        if (!autoDetected) {
            return result;
        }

        Map<String, Object> enriched = new LinkedHashMap<>(result);
        enriched.put("autoDetected", true);
        enriched.put("embeddingModel", autoDetectedModel);
        return enriched;
    }

    public Map<String, Object> detectEmbeddingDimension(String probeText) {
        return detectEmbeddingDimension("default", probeText, null);
    }

    public Map<String, Object> detectEmbeddingDimension(String probeText, String embeddingModel) {
        return detectEmbeddingDimension("default", probeText, embeddingModel);
    }

    public Map<String, Object> detectEmbeddingDimension(String tenantId, String probeText, String embeddingModel) {
        String text = probeText == null || probeText.isBlank() ? "dimension probe" : probeText.trim();
        ChatModelConfig config = resolveEmbeddingConfig(tenantId, embeddingModel);
        List<Double> vector = embedQueryWithConfig(config, text);
        return Map.of(
            "model", config.getModel(),
                "dimension", vector.size(),
                "probeText", text
        );
    }

    /**
     * 调用 OpenAI-compatible embedding 接口，将文本转为向量。
     * POST {embedding.base-url}/embeddings
     * Body: {"model": "...", "input": "..."}
     * Response: {"data": [{"embedding": [...]}]}
     */
    private List<Double> embedQuery(String tenantId, String text, String embeddingModel) {
        ChatModelConfig config = resolveEmbeddingConfig(tenantId, embeddingModel);
        return embedQueryWithConfig(config, text);
    }

    private List<Double> embedQueryWithConfig(ChatModelConfig config, String text) {
        String embeddingUrl = buildEmbeddingUrl(config.getBaseUrl());

        Map<String, Object> reqBody = Map.of(
                "model", config.getModel(),
                "input", text
        );

        Request.Builder builder = new Request.Builder()
                .url(embeddingUrl)
                .post(RequestBody.create(writeJson(reqBody), JSON));
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("embedding failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object dataNode = root.get("data");
            if (!(dataNode instanceof List<?> dataList) || dataList.isEmpty()) {
                throw new RuntimeException("embedding 响应 data 为空: " + clip(responseBody));
            }
            Object firstItem = dataList.get(0);
            if (!(firstItem instanceof Map<?, ?> itemMap)) {
                throw new RuntimeException("embedding 响应 data[0] 格式异常");
            }
            Object embNode = itemMap.get("embedding");
            if (!(embNode instanceof List<?> embList)) {
                throw new RuntimeException("embedding 响应缺少 embedding 字段");
            }
            List<Double> vector = new ArrayList<>(embList.size());
            for (Object v : embList) {
                vector.add(v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v)));
            }
            return vector;
        } catch (IOException e) {
            throw new RuntimeException("embedding request failed", e);
        }
    }

    private ChatModelConfig resolveEmbeddingConfig(String tenantId, String embeddingModel) {
        String resolvedTenant = normalizeTenant(tenantId);
        ChatModelConfig byTenant = queryEmbeddingConfig(resolvedTenant, embeddingModel);
        if (byTenant != null) {
            return byTenant;
        }
        if (!"default".equals(resolvedTenant)) {
            ChatModelConfig byDefault = queryEmbeddingConfig("default", embeddingModel);
            if (byDefault != null) {
                return byDefault;
            }
        }
        String modelHint = embeddingModel == null || embeddingModel.isBlank() ? "<自动选择>" : embeddingModel.trim();
        throw new IllegalStateException("未找到可用的 EMBEDDING 模型配置，请先在模型配置页维护（tenant="
                + resolvedTenant + ", model=" + modelHint + "）");
    }

    private ChatModelConfig queryEmbeddingConfig(String tenantId, String embeddingModel) {
        QueryWrapper<ChatModelConfig> query = new QueryWrapper<ChatModelConfig>()
                .eq("tenant_id", tenantId)
                .eq("config_type", "EMBEDDING")
                .eq("enabled", 1)
                .eq("deleted", 0)
                .orderByDesc("updated_at")
                .orderByDesc("id")
                .last("limit 1");
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            query.eq("model", embeddingModel.trim());
        }
        return modelConfigMapper.selectOne(query);
    }

    private String buildEmbeddingUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("embedding base-url 未配置");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
        if (HttpUrl.parse(url) == null) {
            throw new IllegalStateException("embedding base-url 非法: " + baseUrl);
        }
        return url;
    }

    private String buildQdrantSearchUrl() {
        HttpUrl base = requireQdrantBaseUrl();
        return base.newBuilder()
                .addPathSegment("collections")
                .addPathSegment(properties.getRetrieval().getCollection())
                .addPathSegment("points")
                .addPathSegment("search")
                .build()
                .toString();
    }

    private String buildQdrantUpsertUrl() {
        HttpUrl base = requireQdrantBaseUrl();
        return base.newBuilder()
                .addPathSegment("collections")
                .addPathSegment(properties.getRetrieval().getCollection())
                .addPathSegment("points")
                .build()
                .toString();
    }

    private String buildQdrantCollectionsUrl() {
        HttpUrl base = requireQdrantBaseUrl();
        return base.newBuilder()
                .addPathSegment("collections")
                .build()
                .toString();
    }

    private String buildQdrantCollectionUrl() {
        return buildQdrantCollectionUrl(properties.getRetrieval().getCollection());
    }

    private String buildQdrantCollectionUrl(String collection) {
        HttpUrl base = requireQdrantBaseUrl();
        return base.newBuilder()
                .addPathSegment("collections")
                .addPathSegment(collection)
                .build()
                .toString();
    }

    private String buildQdrantPointsDeleteUrl(String collection) {
        HttpUrl base = requireQdrantBaseUrl();
        return base.newBuilder()
                .addPathSegment("collections")
                .addPathSegment(collection)
                .addPathSegment("points")
                .addPathSegment("delete")
                .build()
                .toString();
    }

    private String buildQdrantPointsScrollUrl(String collection) {
        HttpUrl base = requireQdrantBaseUrl();
        return base.newBuilder()
                .addPathSegment("collections")
                .addPathSegment(collection)
                .addPathSegment("points")
                .addPathSegment("scroll")
                .build()
                .toString();
    }

    private HttpUrl requireQdrantBaseUrl() {
        String endpoint = properties.getRetrieval().getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("qdrant endpoint 未配置");
        }
        HttpUrl base = HttpUrl.parse(endpoint);
        if (base == null) {
            throw new IllegalStateException("qdrant endpoint 非法: " + endpoint);
        }
        return base;
    }

    private Map<String, Object> ensureCollectionExists(int vectorSize, String distance) {
        String collection = properties.getRetrieval().getCollection();
        String effectiveDistance = distance == null || distance.isBlank()
                ? properties.getRetrieval().getVectorDistance()
                : distance;
        String collectionUrl = buildQdrantCollectionUrl();

        Request.Builder checkBuilder = new Request.Builder().url(collectionUrl).get();
        withQdrantAuth(checkBuilder);
        try (Response checkResponse = okHttpClient.newCall(checkBuilder.build()).execute()) {
            String body = checkResponse.body() == null ? "" : checkResponse.body().string();
            if (checkResponse.isSuccessful()) {
                return Map.of(
                        "collection", collection,
                        "vectorSize", vectorSize,
                        "distance", effectiveDistance,
                        "status", "exists"
                );
            }
            if (checkResponse.code() != 404) {
                throw new RuntimeException("qdrant check collection failed, status=" + checkResponse.code() + ", body=" + clip(body));
            }
        } catch (IOException e) {
            throw new RuntimeException("qdrant check collection request failed", e);
        }

        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", effectiveDistance);
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("vectors", vectors);
        createBody.put("hnsw_config", buildHnswConfig());
        Map<String, Object> quantizationConfig = buildQuantizationConfig();
        if (!quantizationConfig.isEmpty()) {
            createBody.put("quantization_config", quantizationConfig);
        }

        Request.Builder createBuilder = new Request.Builder()
                .url(collectionUrl)
                .put(RequestBody.create(writeJson(createBody), JSON));
        withQdrantAuth(createBuilder);
        try (Response createResponse = okHttpClient.newCall(createBuilder.build()).execute()) {
            String body = createResponse.body() == null ? "" : createResponse.body().string();
            if (!createResponse.isSuccessful()) {
                throw new RuntimeException("qdrant create collection failed, status=" + createResponse.code() + ", body=" + clip(body));
            }
            return Map.of(
                    "collection", collection,
                    "vectorSize", vectorSize,
                    "distance", effectiveDistance,
                    "status", "created"
            );
        } catch (IOException e) {
            throw new RuntimeException("qdrant create collection request failed", e);
        }
    }

    private void withQdrantAuth(Request.Builder builder) {
        String apiKey = properties.getRetrieval().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("api-key", apiKey);
        }
    }

    private Map<String, Object> buildHnswConfig() {
        ChatModuleProperties.Retrieval cfg = properties.getRetrieval();
        Map<String, Object> hnsw = new LinkedHashMap<>();
        hnsw.put("m", Math.max(4, cfg.getHnswM()));
        hnsw.put("ef_construct", Math.max(16, cfg.getHnswEfConstruct()));
        hnsw.put("full_scan_threshold", Math.max(100, cfg.getHnswFullScanThreshold()));
        return hnsw;
    }

    private Map<String, Object> buildQuantizationConfig() {
        ChatModuleProperties.Retrieval cfg = properties.getRetrieval();
        String mode = cfg.getQuantizationMode() == null ? "none" : cfg.getQuantizationMode().trim().toLowerCase();
        if ("none".equals(mode) || mode.isBlank()) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if ("scalar".equals(mode)) {
            Map<String, Object> scalar = new LinkedHashMap<>();
            scalar.put("type", "int8");
            scalar.put("always_ram", cfg.isQuantizationAlwaysRam());
            result.put("scalar", scalar);
            return result;
        }

        if ("product".equals(mode) || "pq".equals(mode)) {
            Map<String, Object> product = new LinkedHashMap<>();
            String compression = cfg.getQuantizationCompression();
            product.put("compression", compression == null || compression.isBlank() ? "x32" : compression);
            product.put("always_ram", cfg.isQuantizationAlwaysRam());
            result.put("product", product);
            return result;
        }

        return Map.of();
    }

    private int resolveChunkSize(Integer chunkSize) {
        int configured = properties.getRetrieval().getIndexChunkSize();
        int value = chunkSize == null ? configured : chunkSize;
        return Math.max(100, Math.min(value, 4000));
    }

    private int resolveChunkOverlap(Integer chunkOverlap, int chunkSize) {
        int configured = properties.getRetrieval().getIndexChunkOverlap();
        int value = chunkOverlap == null ? configured : chunkOverlap;
        value = Math.max(0, value);
        return Math.min(value, Math.max(0, chunkSize - 1));
    }

    private List<String> splitIntoChunks(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int step = Math.max(1, chunkSize - chunkOverlap);
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    private int deleteByIds(String collection, List<Object> ids) {
        String endpoint = buildQdrantPointsDeleteUrl(collection);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", ids);
        body.put("wait", true);

        Request.Builder requestBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(writeJson(body), JSON));
        withQdrantAuth(requestBuilder);
        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("qdrant delete points failed, status=" + response.code() + ", body=" + clip(responseBody));
            }
            return ids.size();
        } catch (IOException e) {
            throw new RuntimeException("qdrant delete points request failed", e);
        }
    }

    private String resolveCollection(String collection) {
        String resolved = collection == null ? "" : collection.trim();
        if (resolved.isBlank()) {
            throw new IllegalArgumentException("collection 不能为空");
        }
        return resolved;
    }

    private List<Object> normalizeVectorIds(List<String> ids) {
        List<Object> normalized = new ArrayList<>();
        if (ids == null) {
            return normalized;
        }
        for (String raw : ids) {
            String text = raw == null ? "" : raw.trim();
            if (text.isBlank()) {
                continue;
            }
            try {
                normalized.add(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private List<String> mapCollections(String responseBody) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object resultNode = root.get("result");
            if (!(resultNode instanceof Map<?, ?> resultMap)) {
                return List.of();
            }
            Object collectionsNode = resultMap.get("collections");
            if (!(collectionsNode instanceof List<?> rows)) {
                return List.of();
            }
            List<String> collections = new ArrayList<>();
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                String name = Objects.toString(map.get("name"), "").trim();
                if (!name.isBlank()) {
                    collections.add(name);
                }
            }
            return collections;
        } catch (IOException e) {
            throw new RuntimeException("解析 qdrant collections 响应失败: " + clip(responseBody), e);
        }
    }

    private List<Object> extractPointIds(Object pointsNode) {
        if (!(pointsNode instanceof List<?> points)) {
            return List.of();
        }
        List<Object> ids = new ArrayList<>();
        for (Object pointNode : points) {
            if (!(pointNode instanceof Map<?, ?> pointMap)) {
                continue;
            }
            Object id = pointMap.get("id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Qdrant /points/search 返回格式：{"status":"ok","result":[{"id":"p1","score":0.91,"payload":{...}}]}
     */
    private List<Map<String, Object>> mapSearchHits(String responseBody) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object resultNode = root.get("result");
            if (!(resultNode instanceof List<?> points)) {
                return List.of();
            }
            List<Map<String, Object>> hits = new ArrayList<>();
            for (Object pointNode : points) {
                if (!(pointNode instanceof Map<?, ?> pointMap)) {
                    continue;
                }
                Object payloadNode = pointMap.get("payload");
                Map<?, ?> payload = payloadNode instanceof Map<?, ?> payloadMap ? payloadMap : Map.of();
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("id", pointMap.get("id"));
                Object title = payload.containsKey("title")
                        ? payload.get("title")
                        : payload.get(properties.getRetrieval().getContentField());
                hit.put("title", title == null ? "" : title);
                Object score = pointMap.get("score");
                hit.put("score", score == null ? 0 : score);
                hit.put("payload", payload);
                hits.add(hit);
            }
            return hits;
        } catch (IOException e) {
            throw new RuntimeException("解析 qdrant 响应失败: " + clip(responseBody), e);
        }
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private String clip(String text) {
        String normalized = Objects.toString(text, "").replace('\n', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }
}
