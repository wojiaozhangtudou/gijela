package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatflowCompletionDTO {

    @NotBlank(message = "会话ID不能为空")
    @Size(min = 8, max = 64, message = "会话ID长度需在8-64之间")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "消息内容不能超过8000字符")
    private String content;

    @Size(max = 64, message = "requestId长度不能超过64")
    private String requestId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
