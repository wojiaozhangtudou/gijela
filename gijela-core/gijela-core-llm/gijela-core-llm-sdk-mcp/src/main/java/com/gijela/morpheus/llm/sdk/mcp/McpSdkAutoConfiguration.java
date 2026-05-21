package com.gijela.morpheus.llm.sdk.mcp;

import com.gijela.morpheus.llm.sdk.mcp.client.McpClientFactory;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.client.McpTransport;
import com.gijela.morpheus.llm.sdk.mcp.client.transport.SseTransport;
import com.gijela.morpheus.llm.sdk.mcp.client.transport.StdioTransport;
import com.gijela.morpheus.llm.sdk.mcp.client.transport.StreamableHttpTransport;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpSkillSync;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBindingSource;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import com.gijela.morpheus.llm.sdk.skill.SkillSdkAutoConfiguration;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP SDK 自动装配。引入本 jar 后自动暴露：
 * <ul>
 *   <li>{@link McpSdkProperties} —— 绑定 {@code gijela.llm.mcp.*}</li>
 *   <li>{@link McpJsonRpcClient} —— 多 transport 复合客户端</li>
 *   <li>{@link McpSkillSync} —— 当业务侧提供 {@link McpToolBindingSource} Bean 时启用</li>
 * </ul>
 *
 * <p>注意：本模块不暴露 ApplicationRunner / @Scheduled，业务侧自行决定调用时机。</p>
 */
@AutoConfiguration(after = SkillSdkAutoConfiguration.class)
@EnableConfigurationProperties(McpSdkProperties.class)
public class McpSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpJsonRpcClient mcpJsonRpcClient(McpSdkProperties mcp) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(mcp.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(mcp.getReadTimeoutSeconds()))
                .callTimeout(Duration.ofSeconds(mcp.getCallTimeoutSeconds()))
                .retryOnConnectionFailure(false)
                .build();
        OkHttpClient sseHttpClient = httpClient.newBuilder()
                .readTimeout(Duration.ZERO)
                .callTimeout(Duration.ZERO)
                .build();
        Map<String, McpTransport> transports = new LinkedHashMap<>();
        StreamableHttpTransport http = new StreamableHttpTransport(httpClient);
        transports.put(http.name(), http);
        SseTransport sse = new SseTransport(sseHttpClient, mcp.getCallTimeoutSeconds());
        transports.put(sse.name(), sse);
        StdioTransport stdio = new StdioTransport(
                mcp.isAllowStdio(),
                mcp.getStdioCommandWhitelist(),
                mcp.getStdioStartupTimeoutSeconds(),
                mcp.getStdioCallTimeoutSeconds());
        transports.put(stdio.name(), stdio);
        return new McpJsonRpcClient(new McpClientFactory(transports));
    }

    /**
     * 仅当业务侧提供 {@link McpToolBindingSource} 实现时才装配 {@link McpSkillSync}。
     */
    @Bean
    @ConditionalOnBean(McpToolBindingSource.class)
    @ConditionalOnMissingBean
    public McpSkillSync mcpSkillSync(McpToolBindingSource bindingSource,
                                     SkillRegistry skillRegistry,
                                     McpJsonRpcClient mcpJsonRpcClient,
                                     McpSdkProperties mcpSdkProperties) {
        return new McpSkillSync(bindingSource, skillRegistry, mcpJsonRpcClient, mcpSdkProperties);
    }
}
