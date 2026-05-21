package com.gijela.morpheus.chat.config;

import com.gijela.morpheus.chat.adapter.llm.OpenAiChatAdapter;
import com.gijela.morpheus.chat.adapter.mcp.KnowledgeSearchGateway;
import com.gijela.morpheus.chat.adapter.mcp.McpGateway;
import com.gijela.morpheus.chat.adapter.skill.AttachmentContextSkillProvider;
import com.gijela.morpheus.chat.adapter.skill.KnowledgeSearchSkillProvider;
import com.gijela.morpheus.chat.adapter.skill.OmniRetrievalSkillProvider;
import com.gijela.morpheus.chat.adapter.storage.ObjectStorageGateway;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.mapper.ChatAuditLogMapper;
import com.gijela.morpheus.chat.mapper.ChatConversationMapper;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import com.gijela.morpheus.chat.mapper.ChatMcpServerMapper;
import com.gijela.morpheus.chat.mapper.ChatMessageMapper;
import com.gijela.morpheus.chat.mapper.ChatSkillStateMapper;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.chat.service.ConversationStore;
import com.gijela.morpheus.chat.service.KnowledgeIngestionService;
import com.gijela.morpheus.chat.service.McpServerService;
import com.gijela.morpheus.chat.service.SkillStateService;
import com.gijela.morpheus.chat.service.SseSessionRegistry;
import com.gijela.morpheus.chat.service.graph.GraphCrudService;
import com.gijela.morpheus.chat.service.graph.GraphExtractionService;
import com.gijela.morpheus.chat.service.graph.GraphImportService;
import com.gijela.morpheus.chat.service.impl.DefaultKnowledgeIngestionService;
import com.gijela.morpheus.chat.service.impl.InMemoryConversationStore;
import com.gijela.morpheus.chat.service.impl.MyBatisMcpServerService;
import com.gijela.morpheus.chat.service.impl.MyBatisSkillStateService;
import com.gijela.morpheus.chat.service.impl.MySqlChatAuditService;
import com.gijela.morpheus.chat.service.impl.RedisConversationStore;
import com.gijela.morpheus.chat.service.impl.graph.DefaultGraphCrudService;
import com.gijela.morpheus.chat.service.impl.graph.DefaultGraphExtractionService;
import com.gijela.morpheus.chat.service.impl.graph.DefaultGraphImportService;
import com.gijela.morpheus.chat.repository.graph.Neo4jGraphRepository;
import com.gijela.morpheus.chat.support.graph.GraphExtractionParser;
import com.gijela.morpheus.chat.support.graph.GraphPromptBuilder;
import com.gijela.morpheus.chat.support.graph.GraphIdempotencyStore;
import com.gijela.morpheus.chat.support.graph.GraphPreviewStore;
import com.gijela.morpheus.llm.sdk.mcp.McpSdkProperties;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpSkillSync;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBindingSource;
import com.gijela.morpheus.llm.sdk.skill.EchoSkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import com.gijela.morpheus.llm.sdk.skill.SkillSdkProperties;
import com.gijela.morpheus.llm.sdk.skill.TimeNowSkillProvider;
import okhttp3.OkHttpClient;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class ChatModuleConfig {

    private static final Logger logger = LoggerFactory.getLogger(ChatModuleConfig.class);

    @Bean(name = {"chatSummaryExecutor", "taskExecutor"})
    public Executor chatSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-summary-");
        executor.initialize();
        return executor;
    }

    @Bean
    public OkHttpClient chatOkHttpClient() {
        return new OkHttpClient.Builder().build();
    }


    @Bean
    public ConversationStore conversationStore(ChatModuleProperties properties,
                                               ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                               ChatConversationMapper chatConversationMapper,
                                               ChatMessageMapper chatMessageMapper) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if ("redis".equalsIgnoreCase(properties.getConversation().getStore()) && stringRedisTemplate != null) {
            return new RedisConversationStore(properties, stringRedisTemplate, chatConversationMapper, chatMessageMapper);
        }
        return new InMemoryConversationStore(properties);
    }

    @Bean
    public SseSessionRegistry sseSessionRegistry() {
        return new SseSessionRegistry();
    }

    @Bean
    public KnowledgeSearchGateway knowledgeSearchGateway(ChatModuleProperties properties,
                                                         OkHttpClient chatOkHttpClient,
                                                         ChatModelConfigMapper chatModelConfigMapper) {
        return new KnowledgeSearchGateway(properties, chatOkHttpClient, chatModelConfigMapper);
    }

    @Bean
    public McpGateway mcpGateway(KnowledgeSearchGateway knowledgeSearchGateway) {
        return new McpGateway(knowledgeSearchGateway);
    }

    @Bean
    public KnowledgeIngestionService knowledgeIngestionService(KnowledgeSearchGateway knowledgeSearchGateway,
                                                               ChatModuleProperties properties) {
        return new DefaultKnowledgeIngestionService(knowledgeSearchGateway, properties);
    }

    @Bean
    public ObjectStorageGateway objectStorageGateway(ChatModuleProperties properties,
                                                     OkHttpClient chatOkHttpClient) {
        return new ObjectStorageGateway(properties, chatOkHttpClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gijela.chat.graph", name = "enabled", havingValue = "true")
    public Driver neo4jDriver(ChatModuleProperties properties) {
        ChatModuleProperties.Graph graph = properties.getGraph();
        return GraphDatabase.driver(graph.getUri(), AuthTokens.basic(graph.getUsername(), graph.getPassword()));
    }

    @Bean
    public GraphExtractionParser graphExtractionParser(ChatModuleProperties properties) {
        return new GraphExtractionParser(properties);
    }

    @Bean
    public GraphPromptBuilder graphPromptBuilder(ChatModuleProperties properties) {
        return new GraphPromptBuilder(properties);
    }

    @Bean
    public GraphPreviewStore graphPreviewStore() {
        return new GraphPreviewStore();
    }

    @Bean
    public GraphIdempotencyStore graphIdempotencyStore() {
        return new GraphIdempotencyStore();
    }

    @Bean
    public Neo4jGraphRepository neo4jGraphRepository(ObjectProvider<Driver> neo4jDriverProvider,
                                                     ChatModuleProperties properties) {
        return new Neo4jGraphRepository(neo4jDriverProvider, properties);
    }

    @Bean
    public GraphExtractionService graphExtractionService(ChatModuleProperties properties,
                                                         ChatModelConfigMapper chatModelConfigMapper,
                                                         OkHttpClient chatOkHttpClient,
                                                         GraphExtractionParser graphExtractionParser,
                                                         GraphPromptBuilder graphPromptBuilder,
                                                         GraphPreviewStore graphPreviewStore) {
        return new DefaultGraphExtractionService(properties, chatModelConfigMapper, chatOkHttpClient, graphExtractionParser, graphPromptBuilder, graphPreviewStore);
    }

    @Bean
    public GraphImportService graphImportService(ChatModuleProperties properties,
                                                 GraphPreviewStore graphPreviewStore,
                                                 GraphIdempotencyStore graphIdempotencyStore,
                                                 Neo4jGraphRepository neo4jGraphRepository) {
        return new DefaultGraphImportService(properties, graphPreviewStore, graphIdempotencyStore, neo4jGraphRepository);
    }

    @Bean
    public GraphCrudService graphCrudService(ChatModuleProperties properties,
                                             Neo4jGraphRepository neo4jGraphRepository) {
        return new DefaultGraphCrudService(properties, neo4jGraphRepository);
    }

    /**
     * 业务侧覆盖 SDK 默认 SkillRegistry：显式注册内置 + 业务专属 provider。
     * （SDK 本身零内置，避免业务耦合。）
     */
    @Bean
    public SkillRegistry skillRegistry(SkillSdkProperties skillSdkProperties,
                                       AutowireCapableBeanFactory beanFactory,
                                       McpGateway mcpGateway,
                                       ChatAttachmentMapper chatAttachmentMapper,
                                       KnowledgeSearchGateway knowledgeSearchGateway,
                                       Neo4jGraphRepository neo4jGraphRepository) {
        SkillRegistry registry = new SkillRegistry(skillSdkProperties, beanFactory);
        registerBuiltinIfAbsent(registry, new TimeNowSkillProvider());
        registerBuiltinIfAbsent(registry, new EchoSkillProvider());
        registerBuiltinIfAbsent(registry, new KnowledgeSearchSkillProvider(mcpGateway));
        registerBuiltinIfAbsent(registry, new AttachmentContextSkillProvider(chatAttachmentMapper));
        registerBuiltinIfAbsent(registry, new OmniRetrievalSkillProvider(chatAttachmentMapper, knowledgeSearchGateway, neo4jGraphRepository));
        return registry;
    }

    private void registerBuiltinIfAbsent(SkillRegistry registry, SkillProvider provider) {
        if (registry.getProvider(provider.name()) != null) {
            logger.info("[skills] skip builtin registration because local skill already exists, name={}", provider.name());
            return;
        }
        registry.register(provider);
    }

    @Bean
    public ChatAuditService chatAuditService(ChatAuditLogMapper chatAuditLogMapper) {
        return new MySqlChatAuditService(chatAuditLogMapper);
    }

    @Bean
    public SkillStateService skillStateService(ChatSkillStateMapper chatSkillStateMapper) {
        return new MyBatisSkillStateService(chatSkillStateMapper);
    }

    @Bean
    public OpenAiChatAdapter openAiChatAdapter(OkHttpClient chatOkHttpClient,
                                               ChatModelConfigMapper chatModelConfigMapper,
                                               SkillRegistry skillRegistry,
                                               ChatAuditService chatAuditService,
                                               SkillStateService skillStateService) {
        return new OpenAiChatAdapter(chatOkHttpClient, chatModelConfigMapper, skillRegistry, chatAuditService, skillStateService);
    }

    @Bean
    public McpServerService mcpServerService(ChatMcpServerMapper chatMcpServerMapper,
                                             McpJsonRpcClient mcpJsonRpcClient,
                                             McpSdkProperties mcpSdkProperties,
                                             ObjectProvider<McpSkillSync> mcpSkillSyncProvider) {
        return new MyBatisMcpServerService(chatMcpServerMapper, mcpJsonRpcClient,
                mcpSdkProperties, mcpSkillSyncProvider);
    }

    /**
     * 暴露 {@link MyBatisMcpServerService} 作为 {@link McpToolBindingSource} Bean，
     * 让 sdk-mcp 的 {@link McpSkillSync} 通过接口注入而不感知业务实现。
     */
    @Bean
    @Primary
    public McpToolBindingSource mcpToolBindingSource(McpServerService mcpServerService) {
        return (McpToolBindingSource) mcpServerService;
    }

    /** 启动时触发一次 MCP→Skill 全量同步。{@link McpSkillSync} 由 sdk-mcp 自动装配。 */
    @Bean
    public org.springframework.boot.ApplicationRunner mcpSkillBootstrap(McpSkillSync mcpSkillSync) {
        return args -> mcpSkillSync.initOnStartup();
    }
}
