package com.gijela.morpheus.llm.sdk.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpRpcCodec;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;
import com.gijela.morpheus.llm.sdk.mcp.client.McpTransport;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class StreamableHttpTransport
implements McpTransport {
    static final MediaType JSON = MediaType.parse((String)"application/json; charset=utf-8");
    static final String SESSION_HEADER = "Mcp-Session-Id";
    private final OkHttpClient httpClient;

    public StreamableHttpTransport(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "streamable_http";
    }

    @Override
    public McpSession open(McpEndpointConfig config) {
        if (config.endpoint() == null || config.endpoint().isBlank()) {
            throw new McpClientException("streamable_http transport \u5fc5\u987b\u914d\u7f6e endpoint");
        }
        return new HttpSession(this.httpClient, config.endpoint(), config.bearerToken());
    }

    static String extractFirstSseDataLine(String body) {
        if (body == null) {
            return "{}";
        }
        for (String line : body.split("\\r?\\n")) {
            if (!line.startsWith("data:")) continue;
            return line.substring(5).trim();
        }
        return body.trim();
    }

    static String readSafe(ResponseBody body) {
        if (body == null) {
            return "";
        }
        try {
            return body.string();
        }
        catch (Exception e) {
            return "";
        }
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static class HttpSession
    implements McpSession {
        private final OkHttpClient httpClient;
        private final String endpoint;
        private final String bearerToken;
        private final AtomicLong reqId = new AtomicLong(0L);
        private volatile String sessionId;

        HttpSession(OkHttpClient httpClient, String endpoint, String bearerToken) {
            this.httpClient = httpClient;
            this.endpoint = endpoint;
            this.bearerToken = bearerToken;
        }

        @Override
        public JsonNode call(String method, Map<String, Object> params) {
            JsonNode jsonNode;
            block14: {
                String body = McpRpcCodec.request(this.reqId.incrementAndGet(), method, params);
                Request.Builder builder = new Request.Builder().url(this.endpoint).header("Accept", "application/json, text/event-stream").header("MCP-Protocol-Version", "2024-11-05");
                if (this.bearerToken != null && !this.bearerToken.isBlank()) {
                    builder.header("Authorization", "Bearer " + this.bearerToken);
                }
                if (this.sessionId != null && !this.sessionId.isBlank()) {
                    builder.header(StreamableHttpTransport.SESSION_HEADER, this.sessionId);
                }
                builder.post(RequestBody.create((String)body, (MediaType)JSON));
                Response resp;
                try {
                    resp = this.httpClient.newCall(builder.build()).execute();
                } catch (IOException ioe) {
                    throw new McpClientException("MCP 网络错误: " + ioe.getMessage(), ioe);
                }
                try {
                    ResponseBody rb;
                    if (!resp.isSuccessful()) {
                        String detail = StreamableHttpTransport.readSafe(resp.body());
                        throw new McpClientException("MCP HTTP " + resp.code() + ": " + StreamableHttpTransport.truncate(detail, 256));
                    }
                    String newSession = resp.header(StreamableHttpTransport.SESSION_HEADER);
                    if (newSession != null && !newSession.isBlank()) {
                        this.sessionId = newSession;
                    }
                    if ((rb = resp.body()) == null) {
                        throw new McpClientException("MCP \u54cd\u5e94\u4f53\u4e3a\u7a7a");
                    }
                    String contentType = resp.header("Content-Type", "");
                    String text = rb.string();
                    if (contentType != null && contentType.toLowerCase().contains("text/event-stream")) {
                        text = StreamableHttpTransport.extractFirstSseDataLine(text);
                    }
                    jsonNode = McpRpcCodec.parseResponse(text);
                    if (resp == null) break block14;
                }
                catch (Throwable throwable) {
                    try {
                        if (resp != null) {
                            try {
                                resp.close();
                            }
                            catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    catch (IOException e) {
                        throw new McpClientException("MCP \u7f51\u7edc\u9519\u8bef: " + e.getMessage(), e);
                    }
                }
                resp.close();
            }
            return jsonNode;
        }

        @Override
        public void close() {
        }
    }
}
