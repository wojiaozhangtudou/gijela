package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.event.LlmEvent;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventType;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEventParserTest {

    @Test
    void shouldParseStartDeltaAndDoneEvents() {
        SseEventParser parser = new SseEventParser();

        List<LlmEvent> first = parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
        assertEquals(2, first.size());
        assertEquals(LlmEventType.START, first.get(0).type());
        assertEquals(LlmEventType.DELTA, first.get(1).type());
        assertEquals("你", first.get(1).textDelta());

        List<LlmEvent> second = parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}");
        assertEquals(1, second.size());
        assertEquals(LlmEventType.DELTA, second.get(0).type());
        assertEquals("好", second.get(0).textDelta());

        List<LlmEvent> done = parser.parseLine("data: [DONE]");
        assertEquals(1, done.size());
        assertEquals(LlmEventType.DONE, done.get(0).type());
    }

    @Test
    void shouldAssembleFragmentedToolCallArguments() {
        SseEventParser parser = new SseEventParser();

        List<LlmEvent> first = parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"北\"}}]}}]}"
        );
        assertFalse(first.isEmpty());
        assertEquals(LlmEventType.START, first.get(0).type());

        List<LlmEvent> second = parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"京\\\"}\"}}]}}]}"
        );

        List<LlmEvent> third = parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"
        );

        ToolCall call = findToolCall(second, third);
        assertNotNull(call);
        assertEquals("call_1", call.id());
        assertEquals("weather", call.name());
        assertEquals("北京", String.valueOf(call.arguments().get("city")));
    }

    @Test
    void shouldCaptureUsageFromChunkAfterStopFinishReason() {
        // qwen-plus / OpenAI 在 stream_options.include_usage=true 时，
        // usage 数据在 finish_reason:stop chunk 之后、[DONE] 之前单独发送
        SseEventParser parser = new SseEventParser();

        parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}");
        parser.parseLine("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        // usage-only chunk（choices 为空）
        parser.parseLine("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}");
        List<LlmEvent> doneEvents = parser.parseLine("data: [DONE]");

        assertEquals(1, doneEvents.size());
        assertEquals(LlmEventType.DONE, doneEvents.get(0).type());
        assertNotNull(doneEvents.get(0).usage());
        assertEquals(10, doneEvents.get(0).usage().promptTokens());
        assertEquals(5, doneEvents.get(0).usage().completionTokens());
        assertEquals(15, doneEvents.get(0).usage().totalTokens());
    }

    @Test
    void shouldEmitErrorEventForMalformedChunk() {
        SseEventParser parser = new SseEventParser();
        List<LlmEvent> events = parser.parseLine("data: {\"choices\":[broken]}");

        assertEquals(1, events.size());
        assertEquals(LlmEventType.ERROR, events.get(0).type());
        assertTrue(events.get(0).errorMessage() != null && !events.get(0).errorMessage().isBlank());
    }

    @SafeVarargs
    private static ToolCall findToolCall(List<LlmEvent>... batches) {
        for (List<LlmEvent> batch : batches) {
            for (LlmEvent event : batch) {
                if (event.type() == LlmEventType.TOOL_CALL) {
                    return event.toolCall();
                }
            }
        }
        return null;
    }
}
