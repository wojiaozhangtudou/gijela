package com.gijela.morpheus.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gijela.chat")
public class ChatModuleProperties {

    private final Server server = new Server();
    private final Conversation conversation = new Conversation();
    private final Retrieval retrieval = new Retrieval();
    private final Storage storage = new Storage();
    private final OpenAi openAi = new OpenAi();
    private final Embedding embedding = new Embedding();
    private final AttachmentSummary attachmentSummary = new AttachmentSummary();
    private final Mcp mcp = new Mcp();
    private final Graph graph = new Graph();

    public Server getServer() {
        return server;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Storage getStorage() {
        return storage;
    }

    public OpenAi getOpenAi() {
        return openAi;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public AttachmentSummary getAttachmentSummary() {
        return attachmentSummary;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public Graph getGraph() {
        return graph;
    }

    public static class Server {
        private String tenantHeader = "X-Tenant-Id";
        private String requestHeader = "X-Request-Id";

        public String getTenantHeader() {
            return tenantHeader;
        }

        public void setTenantHeader(String tenantHeader) {
            this.tenantHeader = tenantHeader;
        }

        public String getRequestHeader() {
            return requestHeader;
        }

        public void setRequestHeader(String requestHeader) {
            this.requestHeader = requestHeader;
        }
    }

    public static class Conversation {
        private int recentMessageRounds = 20;
        private int summaryRefreshRounds = 10;
        private int summaryRefreshTokenThreshold = 6000;
        private String store = "redis";
        private String redisKeyPrefix = "chat:session";

        public int getRecentMessageRounds() {
            return recentMessageRounds;
        }

        public void setRecentMessageRounds(int recentMessageRounds) {
            this.recentMessageRounds = recentMessageRounds;
        }

        public int getSummaryRefreshRounds() {
            return summaryRefreshRounds;
        }

        public void setSummaryRefreshRounds(int summaryRefreshRounds) {
            this.summaryRefreshRounds = summaryRefreshRounds;
        }

        public int getSummaryRefreshTokenThreshold() {
            return summaryRefreshTokenThreshold;
        }

        public void setSummaryRefreshTokenThreshold(int summaryRefreshTokenThreshold) {
            this.summaryRefreshTokenThreshold = summaryRefreshTokenThreshold;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }
    }

    public static class Retrieval {
        private String collection = "chat_knowledge";
        private boolean enabled = true;
        private String endpoint = "http://127.0.0.1:6333";
        private String apiKey;
        private String tenantField = "tenant_id";
        private String contentField = "content";
        private int topK = 5;
        private int indexChunkSize = 500;
        private int indexChunkOverlap = 100;
        private int indexFileMaxChars = 200000;
        private String vectorDistance = "Cosine";
        private boolean autoCreateCollection = true;
        private int hnswM = 16;
        private int hnswEfConstruct = 128;
        private int hnswFullScanThreshold = 10000;
        private String quantizationMode = "none";
        private String quantizationCompression = "x32";
        private boolean quantizationAlwaysRam = false;

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getTenantField() {
            return tenantField;
        }

        public void setTenantField(String tenantField) {
            this.tenantField = tenantField;
        }

        public String getContentField() {
            return contentField;
        }

        public void setContentField(String contentField) {
            this.contentField = contentField;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getIndexChunkSize() {
            return indexChunkSize;
        }

        public void setIndexChunkSize(int indexChunkSize) {
            this.indexChunkSize = indexChunkSize;
        }

        public int getIndexChunkOverlap() {
            return indexChunkOverlap;
        }

        public void setIndexChunkOverlap(int indexChunkOverlap) {
            this.indexChunkOverlap = indexChunkOverlap;
        }

        public int getIndexFileMaxChars() {
            return indexFileMaxChars;
        }

        public void setIndexFileMaxChars(int indexFileMaxChars) {
            this.indexFileMaxChars = indexFileMaxChars;
        }

        public String getVectorDistance() {
            return vectorDistance;
        }

        public void setVectorDistance(String vectorDistance) {
            this.vectorDistance = vectorDistance;
        }

        public boolean isAutoCreateCollection() {
            return autoCreateCollection;
        }

        public void setAutoCreateCollection(boolean autoCreateCollection) {
            this.autoCreateCollection = autoCreateCollection;
        }

        public int getHnswM() {
            return hnswM;
        }

        public void setHnswM(int hnswM) {
            this.hnswM = hnswM;
        }

        public int getHnswEfConstruct() {
            return hnswEfConstruct;
        }

        public void setHnswEfConstruct(int hnswEfConstruct) {
            this.hnswEfConstruct = hnswEfConstruct;
        }

        public int getHnswFullScanThreshold() {
            return hnswFullScanThreshold;
        }

        public void setHnswFullScanThreshold(int hnswFullScanThreshold) {
            this.hnswFullScanThreshold = hnswFullScanThreshold;
        }

        public String getQuantizationMode() {
            return quantizationMode;
        }

        public void setQuantizationMode(String quantizationMode) {
            this.quantizationMode = quantizationMode;
        }

        public String getQuantizationCompression() {
            return quantizationCompression;
        }

        public void setQuantizationCompression(String quantizationCompression) {
            this.quantizationCompression = quantizationCompression;
        }

        public boolean isQuantizationAlwaysRam() {
            return quantizationAlwaysRam;
        }

        public void setQuantizationAlwaysRam(boolean quantizationAlwaysRam) {
            this.quantizationAlwaysRam = quantizationAlwaysRam;
        }
    }

    public static class Storage {
        private String mode = "gateway";
        private String bucket = "chat-attachments";
        private String keyPattern = "tenant/session/yyyyMMdd/uuid.ext";
        private String endpoint = "http://127.0.0.1:9000";
        private String uploadPath = "/api/v1/storage/objects/upload";
        private String authToken;
        private String accessKey;
        private String secretKey;
        private String region = "us-east-1";
        private boolean s3PathStyle = true;
        private String provider = "rustfs";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getKeyPattern() {
            return keyPattern;
        }

        public void setKeyPattern(String keyPattern) {
            this.keyPattern = keyPattern;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getUploadPath() {
            return uploadPath;
        }

        public void setUploadPath(String uploadPath) {
            this.uploadPath = uploadPath;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public boolean isS3PathStyle() {
            return s3PathStyle;
        }

        public void setS3PathStyle(boolean s3PathStyle) {
            this.s3PathStyle = s3PathStyle;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class OpenAi {
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String apiKey = "demo-key";
        private String model = "gpt-4o-mini";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 120;
        private int callTimeoutSeconds = 180;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public int getCallTimeoutSeconds() {
            return callTimeoutSeconds;
        }

        public void setCallTimeoutSeconds(int callTimeoutSeconds) {
            this.callTimeoutSeconds = callTimeoutSeconds;
        }
    }

    public static class Embedding {
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String apiKey = "";
        private String model = "nomic-embed-text";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }
    }

    public static class AttachmentSummary {
        /** 浓缩 prompt 模板，{text} 占位符会被替换为原始文本 */
        private String condensedPrompt = "请将以下文档内容压缩为原文30%左右的Markdown格式，保留关键信息、数据和结论，去除冗余描述：\n\n{text}";
        /** 摘要 prompt 模板，{text} 占位符会被替换为浓缩后的内容 */
        private String summaryPrompt = "请用不超过50个字对以下内容进行一句话摘要：\n\n{text}";
        /** 原始文本最大字符数，超出截断，0=不限制 */
        private int maxRawChars = 50000;

        public String getCondensedPrompt() {
            return condensedPrompt;
        }

        public void setCondensedPrompt(String condensedPrompt) {
            this.condensedPrompt = condensedPrompt;
        }

        public String getSummaryPrompt() {
            return summaryPrompt;
        }

        public void setSummaryPrompt(String summaryPrompt) {
            this.summaryPrompt = summaryPrompt;
        }

        public int getMaxRawChars() {
            return maxRawChars;
        }

        public void setMaxRawChars(int maxRawChars) {
            this.maxRawChars = maxRawChars;
        }
    }

    /**
     * chat 业务侧 MCP 配置（保留类以兼容注入）。
     */
    public static class Mcp {
    }

    public static class Graph {
        private boolean enabled = false;
        private String uri = "bolt://127.0.0.1:7687";
        private String username = "neo4j";
        private String password;
        private String database = "neo4j";
        private String defaultSpace = "default";
        private int maxFileSizeMb = 10;
        private int maxTextChars = 50000;
        private int maxPreviewEntities = 300;
        private int maxPreviewRelationships = 800;
        private int maxTokens = 4096;
        private java.util.List<String> debugPromptOperators = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getDefaultSpace() {
            return defaultSpace;
        }

        public void setDefaultSpace(String defaultSpace) {
            this.defaultSpace = defaultSpace;
        }

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getMaxTextChars() {
            return maxTextChars;
        }

        public void setMaxTextChars(int maxTextChars) {
            this.maxTextChars = maxTextChars;
        }

        public int getMaxPreviewEntities() {
            return maxPreviewEntities;
        }

        public void setMaxPreviewEntities(int maxPreviewEntities) {
            this.maxPreviewEntities = maxPreviewEntities;
        }

        public int getMaxPreviewRelationships() {
            return maxPreviewRelationships;
        }

        public void setMaxPreviewRelationships(int maxPreviewRelationships) {
            this.maxPreviewRelationships = maxPreviewRelationships;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public java.util.List<String> getDebugPromptOperators() {
            return debugPromptOperators;
        }

        public void setDebugPromptOperators(java.util.List<String> debugPromptOperators) {
            this.debugPromptOperators = debugPromptOperators;
        }
    }
}
