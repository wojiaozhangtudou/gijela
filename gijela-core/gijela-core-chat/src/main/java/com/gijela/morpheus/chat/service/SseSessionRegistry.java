package com.gijela.morpheus.chat.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SseSessionRegistry {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public void register(String sessionId, SseEmitter emitter) {
        emitterMap.put(sessionId, emitter);
    }

    public void remove(String sessionId) {
        emitterMap.remove(sessionId);
    }

    public SseEmitter get(String sessionId) {
        return emitterMap.get(sessionId);
    }
}
