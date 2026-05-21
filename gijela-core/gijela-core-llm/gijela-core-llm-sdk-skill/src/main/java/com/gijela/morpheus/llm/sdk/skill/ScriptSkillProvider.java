package com.gijela.morpheus.llm.sdk.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ScriptSkillProvider implements SkillProvider {

    private final SkillManifest manifest;
    private final String command;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final Set<String> AGENT_BROWSER_SLOW_COMMANDS = Set.of("open", "snapshot");

    public ScriptSkillProvider(SkillManifest manifest) {
        this.manifest = manifest;
        this.command = manifest.entry().substring("script:".length()).trim();
    }

    @Override
    public String name() {
        return manifest.name();
    }

    @Override
    public String description() {
        return manifest.description() == null ? "" : manifest.description();
    }

    @Override
    public String version() {
        return manifest.version() == null ? "1.0.0" : manifest.version();
    }

    @Override
    public SkillSource source() {
        return SkillSource.LOCAL;
    }

    public SkillManifest manifest() {
        return manifest;
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(),
                manifest.description(),
                Map.of(
                        "command", Map.of("type", "string", "description", "子命令，例如 open/snapshot/click"),
                        "args", Map.of("type", "array", "items", Map.of("type", "string"), "description", "额外参数列表")
                ));
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        if (command.isBlank()) {
            return new ToolResult(call.id(), false, Map.of(), "script entry 未配置命令");
        }

        List<String> rawCmd = new ArrayList<>();
        rawCmd.add(command);
        Object sub = call.arguments() == null ? null : call.arguments().get("command");
        Object argsObj = call.arguments() == null ? null : call.arguments().get("args");
        if (sub != null && !String.valueOf(sub).isBlank()) {
            String subText = String.valueOf(sub).trim();
            // 兼容模型把完整命令行放进 command 字段："open https://..."
            // 当 args 未单独给出时，自动按 shell 规则拆分。
            if (!(argsObj instanceof List<?>) && hasBlank(subText)) {
                rawCmd.addAll(splitCommandLine(subText));
            } else {
                rawCmd.add(subText);
            }
        }
        if (argsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    rawCmd.add(String.valueOf(item));
                }
            }
        }

        normalizeAgentBrowserOpenUrl(rawCmd);

        // Windows 下很多 CLI 通过 .cmd 暴露，直接 ProcessBuilder("agent-browser") 可能找不到。
        // 通过 cmd /c 交给 shell 解析可执行文件与 PATHEXT，保持 Claude 风格 skills 的通用兼容性。
        List<String> cmd = WINDOWS ? wrapWindowsShell(rawCmd) : rawCmd;

        Process process;
        try {
            process = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            return new ToolResult(call.id(), false, Map.of(), "脚本技能启动失败: " + e.getMessage());
        }

        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

        long timeoutMs = resolveTimeoutMs(rawCmd);
        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new ToolResult(call.id(), false, Map.of(), "脚本技能执行被中断");
        }

        if (!finished) {
            process.destroyForcibly();
            Map<String, Object> timeoutResult = new LinkedHashMap<>();
            timeoutResult.put("command", cmd);
            timeoutResult.put("timeoutMs", timeoutMs);
            timeoutResult.put("stdout", getFutureResult(stdoutFuture, 500));
            timeoutResult.put("stderr", getFutureResult(stderrFuture, 500));
            return new ToolResult(call.id(), false, timeoutResult, "脚本技能执行超时");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exitCode", process.exitValue());
        result.put("command", cmd);
        result.put("stdout", getFutureResult(stdoutFuture, 2000));
        result.put("stderr", getFutureResult(stderrFuture, 2000));
        boolean ok = process.exitValue() == 0;
        return new ToolResult(call.id(), ok, result, ok ? null : "脚本技能返回非零退出码");
    }

    private long resolveTimeoutMs(List<String> rawCmd) {
        if (manifest.timeoutMs() != null) {
            return Math.max(1000L, manifest.timeoutMs());
        }
        if (isAgentBrowserSlowCommand(rawCmd)) {
            return 30000L;
        }
        return 10000L;
    }

    private boolean isAgentBrowserSlowCommand(List<String> rawCmd) {
        if (rawCmd == null || rawCmd.size() < 2) {
            return false;
        }
        if (!"agent-browser".equalsIgnoreCase(rawCmd.get(0))) {
            return false;
        }
        return AGENT_BROWSER_SLOW_COMMANDS.contains(rawCmd.get(1).toLowerCase());
    }

    private void normalizeAgentBrowserOpenUrl(List<String> rawCmd) {
        if (rawCmd == null || rawCmd.size() < 3) {
            return;
        }
        if (!"agent-browser".equalsIgnoreCase(rawCmd.get(0))) {
            return;
        }
        if (!"open".equalsIgnoreCase(rawCmd.get(1))) {
            return;
        }
        String url = rawCmd.get(2);
        if (url == null || url.isBlank()) {
            return;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return;
        }
        rawCmd.set(2, "https://" + url);
    }

    private String getFutureResult(CompletableFuture<String> future, long waitMs) {
        try {
            return future.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }

    private List<String> wrapWindowsShell(List<String> rawCmd) {
        List<String> wrapped = new ArrayList<>();
        wrapped.add("cmd");
        wrapped.add("/c");
        String shellLine = rawCmd.stream().map(this::quoteForCmd).collect(java.util.stream.Collectors.joining(" "));
        wrapped.add(shellLine);
        return wrapped;
    }

    private String quoteForCmd(String token) {
        if (token == null) {
            return "\"\"";
        }
        if (token.contains(" ") || token.contains("\t") || token.contains("\"") || token.contains("&")) {
            return "\"" + token.replace("\"", "\\\"") + "\"";
        }
        return token;
    }

    private boolean hasBlank(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitCommandLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) {
            out.add(cur.toString());
        }
        return out;
    }

    private String readStream(java.io.InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
