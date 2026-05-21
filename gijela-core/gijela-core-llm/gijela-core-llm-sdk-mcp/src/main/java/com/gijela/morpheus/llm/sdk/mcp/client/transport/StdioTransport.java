package com.gijela.morpheus.llm.sdk.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpRpcCodec;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;
import com.gijela.morpheus.llm.sdk.mcp.client.McpTransport;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StdioTransport
implements McpTransport {
    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);
    private final long startupTimeoutSeconds;
    private final long callTimeoutSeconds;
    private final boolean allowStdio;
    private final Set<String> commandWhitelist;

    public StdioTransport(boolean allowStdio, List<String> commandWhitelist, long startupTimeoutSeconds, long callTimeoutSeconds) {
        this.allowStdio = allowStdio;
        this.commandWhitelist = commandWhitelist == null ? Set.of() : new HashSet<String>(commandWhitelist);
        this.startupTimeoutSeconds = startupTimeoutSeconds;
        this.callTimeoutSeconds = callTimeoutSeconds;
    }

    @Override
    public String name() {
        return "stdio";
    }

    @Override
    public McpSession open(McpEndpointConfig config) {
        Process process;
        if (!this.allowStdio) {
            throw new McpClientException("stdio transport \u5df2\u88ab\u7981\u7528\uff0c\u8bf7\u8bbe\u7f6e chat.mcp.allow-stdio=true \u540e\u91cd\u542f");
        }
        if (config.command() == null || config.command().isBlank()) {
            throw new McpClientException("stdio transport \u5fc5\u987b\u914d\u7f6e command");
        }
        if (!this.commandWhitelist.isEmpty() && !this.commandWhitelist.contains(config.command())) {
            throw new McpClientException("stdio command \u4e0d\u5728\u767d\u540d\u5355\u5185: " + config.command());
        }
        ArrayList<String> argv = new ArrayList<String>();
        argv.add(config.command());
        if (config.args() != null) {
            argv.addAll(config.args());
        }
        ProcessBuilder pb = new ProcessBuilder(argv);
        if (config.workingDir() != null && !config.workingDir().isBlank()) {
            pb.directory(new File(config.workingDir()));
        }
        if (config.env() != null && !config.env().isEmpty()) {
            pb.environment().putAll(config.env());
        }
        pb.redirectErrorStream(false);
        try {
            process = pb.start();
        }
        catch (IOException e) {
            throw new McpClientException("stdio \u5b50\u8fdb\u7a0b\u542f\u52a8\u5931\u8d25: " + e.getMessage(), e);
        }
        return new StdioSession(process, this.startupTimeoutSeconds, this.callTimeoutSeconds);
    }

    private static class StdioSession
    implements McpSession {
        private final Process process;
        private final BufferedReader stdout;
        private final BufferedWriter stdin;
        private final Thread readerThread;
        private final Thread errorThread;
        private final AtomicLong reqId = new AtomicLong(0L);
        private final ConcurrentMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<Long, CompletableFuture<JsonNode>>();
        private final long callTimeoutSeconds;
        private volatile boolean closed;

        StdioSession(Process process, long startupTimeoutSeconds, long callTimeoutSeconds) {
            this.process = process;
            this.callTimeoutSeconds = callTimeoutSeconds;
            this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.readerThread = new Thread(this::readLoop, "mcp-stdio-reader-" + process.pid());
            this.readerThread.setDaemon(true);
            this.readerThread.start();
            this.errorThread = new Thread(() -> {
                try (BufferedReader er = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));){
                    String line;
                    while ((line = er.readLine()) != null) {
                        log.debug("[mcp-stdio] stderr pid={}: {}", (Object)process.pid(), (Object)line);
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }, "mcp-stdio-stderr-" + process.pid());
            this.errorThread.setDaemon(true);
            this.errorThread.start();
            try {
                if (process.waitFor(Math.min(startupTimeoutSeconds, 1L), TimeUnit.SECONDS)) {
                    int code = process.exitValue();
                    throw new McpClientException("stdio \u5b50\u8fdb\u7a0b\u542f\u52a8\u540e\u7acb\u5373\u9000\u51fa exitCode=" + code);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpClientException("stdio \u542f\u52a8\u7b49\u5f85\u88ab\u4e2d\u65ad", e);
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        @Override
        public JsonNode call(String method, Map<String, Object> params) {
            if (this.closed || !this.process.isAlive()) {
                throw new McpClientException("stdio \u4f1a\u8bdd\u5df2\u7ed3\u675f");
            }
            long id = this.reqId.incrementAndGet();
            String body = McpRpcCodec.request(id, method, params);
            CompletableFuture future = new CompletableFuture();
            this.pending.put(id, future);
            try {
                BufferedWriter bufferedWriter = this.stdin;
                synchronized (bufferedWriter) {
                    this.stdin.write(body);
                    this.stdin.write(10);
                    this.stdin.flush();
                }
                JsonNode result = (JsonNode)future.get(this.callTimeoutSeconds, TimeUnit.SECONDS);
                return result;
            }
            catch (TimeoutException te) {
                throw new McpClientException("stdio \u8c03\u7528\u8d85\u65f6: method=" + method, te);
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
                throw new McpClientException("stdio \u8c03\u7528\u5931\u8d25: " + e.getMessage(), e);
            }
            finally {
                this.pending.remove(id);
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         * Enabled aggressive block sorting
         * Enabled unnecessary exception pruning
         * Enabled aggressive exception aggregation
         */
        private void readLoop() {
            try {
                while (!this.closed) {
                    String line = this.stdout.readLine();
                    if (line == null) return;
                    if ((line = line.trim()).isEmpty()) continue;
                    try {
                        long rid;
                        CompletableFuture f2;
                        JsonNode root = McpRpcCodec.mapper().readTree(line);
                        JsonNode idNode = root.get("id");
                        if (idNode == null || idNode.isNull() || (f2 = (CompletableFuture)this.pending.get(rid = idNode.asLong())) == null) continue;
                        JsonNode error = root.get("error");
                        if (error != null && !error.isNull()) {
                            f2.completeExceptionally(new McpClientException("MCP \u534f\u8bae\u9519\u8bef code=" + error.path("code").asInt(-1) + ", msg=" + error.path("message").asText("unknown")));
                            continue;
                        }
                        f2.complete(root.path("result"));
                    }
                    catch (Exception parseEx) {
                        log.warn("[mcp-stdio] parse line failed: {}", (Object)parseEx.getMessage());
                    }
                }
                return;
            }
            catch (IOException e) {
                if (this.closed) return;
                log.warn("[mcp-stdio] reader IO error: {}", (Object)e.getMessage());
                return;
            }
            finally {
                McpClientException eof = new McpClientException("stdio \u5b50\u8fdb\u7a0b\u5df2\u9000\u51fa");
                this.pending.values().forEach(f -> f.completeExceptionally(eof));
            }
        }

        @Override
        public synchronized void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                this.stdin.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
            try {
                this.stdout.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (this.process.isAlive()) {
                this.process.destroy();
                try {
                    if (!this.process.waitFor(2L, TimeUnit.SECONDS)) {
                        this.process.destroyForcibly();
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    this.process.destroyForcibly();
                }
            }
        }
    }
}
