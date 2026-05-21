package com.gijela.morpheus.chatflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat-flow.llm")
public class ChatFlowLlmProperties {

    private String apiKey;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 120;
    private int callTimeoutSeconds = 180;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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
