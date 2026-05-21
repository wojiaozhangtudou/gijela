package com.gijela.morpheus.chatflow.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatFlowLlmProperties.class)
public class LlmClientConfig {

    @Bean
    public OkHttpClient chatFlowOkHttpClient() {
        return new OkHttpClient.Builder().build();
    }
}
