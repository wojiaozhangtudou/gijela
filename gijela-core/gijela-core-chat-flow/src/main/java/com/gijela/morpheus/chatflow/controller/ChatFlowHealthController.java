package com.gijela.morpheus.chatflow.controller;

import com.gijela.morpheus.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat-flow")
public class ChatFlowHealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "chat-flow");
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        return ApiResponse.ok(data);
    }
}
