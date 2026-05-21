package com.gijela.morpheus.llm.sdk.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpRpcCodec;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;
import com.gijela.morpheus.llm.sdk.mcp.client.McpTransport;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseTransport
implements McpTransport {
    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);
    private static final MediaType JSON = MediaType.parse((String)"application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final long callTimeoutSeconds;

    public SseTransport(OkHttpClient httpClient, long callTimeoutSeconds) {
        this.httpClient = httpClient;
        this.callTimeoutSeconds = callTimeoutSeconds;
    }

    @Override
    public String name() {
        return "sse";
    }

    @Override
    public McpSession open(McpEndpointConfig config) {
        if (config.endpoint() == null || config.endpoint().isBlank()) {
            throw new McpClientException("sse transport \u5fc5\u987b\u914d\u7f6e endpoint");
        }
        SseSession session = new SseSession(this.httpClient, config.endpoint(), config.bearerToken(), this.callTimeoutSeconds);
        session.connect();
        return session;
    }

    private static class SseSession
    implements McpSession {
        private final OkHttpClient httpClient;
        private final String sseUrl;
        private final String bearerToken;
        private final long callTimeoutSeconds;
        private final AtomicLong reqId = new AtomicLong(0L);
        private final ConcurrentMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<Long, CompletableFuture<JsonNode>>();
        private final CompletableFuture<String> messageEndpointFuture = new CompletableFuture();
        private volatile EventSource eventSource;
        private volatile boolean closed;

        SseSession(OkHttpClient httpClient, String sseUrl, String bearerToken, long callTimeoutSeconds) {
            this.httpClient = httpClient;
            this.sseUrl = sseUrl;
            this.bearerToken = bearerToken;
            this.callTimeoutSeconds = callTimeoutSeconds;
        }

        void connect() {
            Request.Builder b = new Request.Builder().url(this.sseUrl).header("Accept", "text/event-stream").header("MCP-Protocol-Version", "2024-11-05");
            if (this.bearerToken != null && !this.bearerToken.isBlank()) {
                b.header("Authorization", "Bearer " + this.bearerToken);
            }
            this.eventSource = EventSources.createFactory((Call.Factory)this.httpClient).newEventSource(b.build(), (EventSourceListener)new Listener());
            try {
                this.messageEndpointFuture.get(Math.max(this.callTimeoutSeconds, 5L), TimeUnit.SECONDS);
            }
            catch (Exception e) {
                this.close();
                throw new McpClientException("SSE \u8fde\u63a5\u5efa\u7acb\u5931\u8d25: " + e.getMessage(), e);
            }
        }

        @Override
        public JsonNode call(String method, Map<String, Object> params) {
            String messageEndpoint;
            if (this.closed) {
                throw new McpClientException("SSE \u4f1a\u8bdd\u5df2\u5173\u95ed");
            }
            long id = this.reqId.incrementAndGet();
            String body = McpRpcCodec.request(id, method, params);
            try {
                messageEndpoint = this.messageEndpointFuture.get(2L, TimeUnit.SECONDS);
            }
            catch (Exception e) {
                throw new McpClientException("SSE \u6d88\u606f\u7aef\u70b9\u5c1a\u672a\u5c31\u7eea", e);
            }
            CompletableFuture future = new CompletableFuture();
            this.pending.put(id, future);
            try {
                Request.Builder b = new Request.Builder().url(messageEndpoint).header("Accept", "application/json").header("MCP-Protocol-Version", "2024-11-05");
                if (this.bearerToken != null && !this.bearerToken.isBlank()) {
                    b.header("Authorization", "Bearer " + this.bearerToken);
                }
                b.post(RequestBody.create((String)body, (MediaType)JSON));
                try (Response resp = this.httpClient.newCall(b.build()).execute();){
                    if (!resp.isSuccessful() && resp.code() != 202) {
                        throw new McpClientException("SSE POST HTTP " + resp.code());
                    }
                }
                catch (IOException e) {
                    throw new McpClientException("SSE POST \u5931\u8d25: " + e.getMessage(), e);
                }
                JsonNode e = (JsonNode)future.get(this.callTimeoutSeconds, TimeUnit.SECONDS);
                return e;
            }
            catch (TimeoutException te) {
                throw new McpClientException("SSE \u8c03\u7528\u8d85\u65f6: method=" + method, te);
            }
            catch (McpClientException e) {
                throw e;
            }
            catch (Exception e) {
                Throwable throwable = e.getCause();
                if (throwable instanceof McpClientException) {
                    McpClientException ce = (McpClientException)throwable;
                    throw ce;
                }
                throw new McpClientException("SSE \u8c03\u7528\u5931\u8d25: " + e.getMessage(), e);
            }
            finally {
                this.pending.remove(id);
            }
        }

        @Override
        public synchronized void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.eventSource != null) {
                try {
                    this.eventSource.cancel();
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            if (!this.messageEndpointFuture.isDone()) {
                this.messageEndpointFuture.completeExceptionally(new McpClientException("SSE session closed"));
            }
            this.pending.values().forEach(f -> f.completeExceptionally(new McpClientException("SSE session closed")));
            this.pending.clear();
        }

        static String resolveEndpoint(String sseUrl, String dataLine) {
            if (dataLine.startsWith("http://") || dataLine.startsWith("https://")) {
                return dataLine;
            }
            return URI.create(sseUrl).resolve(dataLine).toString();
        }

        private class Listener
        extends EventSourceListener {
            private Listener() {
            }

            public void onEvent(EventSource es, String id, String type, String data) {
                try {
                    if ("endpoint".equalsIgnoreCase(type)) {
                        SseSession.this.messageEndpointFuture.complete(SseSession.resolveEndpoint(SseSession.this.sseUrl, data.trim()));
                        return;
                    }
                    if (type == null || "message".equalsIgnoreCase(type)) {
                        JsonNode root = McpRpcCodec.mapper().readTree(data);
                        JsonNode idNode = root.get("id");
                        if (idNode == null || idNode.isNull()) {
                            return;
                        }
                        long rid = idNode.asLong();
                        CompletableFuture f = (CompletableFuture)SseSession.this.pending.get(rid);
                        if (f == null) {
                            return;
                        }
                        JsonNode error = root.get("error");
                        if (error != null && !error.isNull()) {
                            f.completeExceptionally(new McpClientException("MCP \u534f\u8bae\u9519\u8bef code=" + error.path("code").asInt(-1) + ", msg=" + error.path("message").asText("unknown")));
                        } else {
                            f.complete(root.path("result"));
                        }
                    }
                }
                catch (Exception e) {
                    log.warn("[mcp-sse] handle event failed: type={}, err={}", (Object)type, (Object)e.getMessage());
                }
            }

            public void onFailure(EventSource es, Throwable t, Response resp) {
                String reason = t != null ? t.getMessage() : (resp != null ? "HTTP " + resp.code() : "unknown");
                McpClientException ex = new McpClientException("SSE \u901a\u9053\u5f02\u5e38: " + reason, t);
                if (!SseSession.this.messageEndpointFuture.isDone()) {
                    SseSession.this.messageEndpointFuture.completeExceptionally(ex);
                }
                SseSession.this.pending.values().forEach(f -> f.completeExceptionally(ex));
                SseSession.this.close();
            }

            public void onClosed(EventSource es) {
                SseSession.this.close();
            }
        }
    }
}
