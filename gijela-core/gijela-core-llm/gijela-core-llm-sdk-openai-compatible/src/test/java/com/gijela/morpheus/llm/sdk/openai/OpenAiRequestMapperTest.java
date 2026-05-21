package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiRequestMapperTest {

    @Test
    void shouldMapStreamFlagAndToolMetadata() {
        OpenAiRequestMapper mapper = new OpenAiRequestMapper();
        OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                "https://api.openai.example/v1",
                "sk-test",
                "gpt-test",
                3,
                60,
                65
        );

        List<Map<String, Object>> tools = List.of(
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "weather",
                                "description", "获取天气",
                                "parameters", Map.of("type", "object")
                        )
                )
        );

        ChatRequest request = new ChatRequest(
                null,
                List.of(new ChatMessage("user", "北京天气怎么样")),
                0.2,
                256,
                Map.of(
                        "tools", tools,
                        "tool_choice", "auto"
                )
        );

        Map<String, Object> payload = mapper.toOpenAiRequest(request, properties, true);

        assertEquals("gpt-test", payload.get("model"));
        assertEquals(true, payload.get("stream"));
        assertEquals("auto", payload.get("tool_choice"));
        assertTrue(payload.containsKey("tools"));

        Object messagesObj = payload.get("messages");
        assertTrue(messagesObj instanceof List<?>);
        assertFalse(((List<?>) messagesObj).isEmpty());
    }
}
