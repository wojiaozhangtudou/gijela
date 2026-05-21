package com.gijela.morpheus.chat.adapter.skill;

import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillSchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * agent-browser CLI 包装技能。
 *
 * <p>调用本地已安装的 {@code agent-browser} CLI，支持网页导航、截图、点击、表单填写等自动化操作。
 * 使用前确保已执行：
 * <pre>
 *   npm install -g agent-browser
 *   agent-browser install
 * </pre>
 */
public class AgentBrowserSkillProvider implements SkillProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentBrowserSkillProvider.class);

    /** CLI 可执行文件名，Windows 上优先用 .cmd 版本 */
    private static final String CLI = isWindows() ? "agent-browser.cmd" : "agent-browser";

    /** 单条命令最长等待时间（秒） */
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public String name() {
        return "agent.browser";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(),
                "控制本地浏览器自动化工具 agent-browser，支持网页打开、截图、点击、填表、获取内容等操作。" +
                "使用前先 open 打开页面，再 snapshot 获取元素引用，然后再操作。",
                Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description",
                                "完整的 agent-browser 子命令及参数，不含 'agent-browser' 前缀。" +
                                "示例：'open https://www.baidu.com'  |  'snapshot -i'  |  'click @e1'  |  " +
                                "'fill @e2 \"搜索词\"'  |  'get text @e1'  |  'close'"
                        ),
                        "session", Map.of(
                                "type", "string",
                                "description", "可选，指定隔离会话名称，不填则使用默认会话"
                        ),
                        "json", Map.of(
                                "type", "boolean",
                                "description", "可选，返回 JSON 格式输出，便于解析，默认 false"
                        ),
                        "timeoutMs", Map.of(
                                "type", "integer",
                                "description", "可选，命令超时毫秒数，默认 30000"
                        )
                ),
                List.of("command"));
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        Map<String, Object> args = call.arguments();
        String command = argString(args, "command");
        if (command == null || command.isBlank()) {
            return new ToolResult(call.id(), false, Map.of(), "command 参数不能为空");
        }

        String session  = argString(args, "session");
        boolean useJson = argBool(args, "json", false);
        int timeoutMs   = argInt(args, "timeoutMs", TIMEOUT_SECONDS * 1000, 1000, 120_000);

        // ── 构建命令行 ─────────────────────────────────────────────────────────
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(CLI);
        if (session != null && !session.isBlank()) {
            cmdLine.add("--session");
            cmdLine.add(session.trim());
        }
        if (useJson) {
            cmdLine.add("--json");
        }
        // 把 command 字符串按空格切分，但保留引号内的内容
        cmdLine.addAll(splitCommand(command));

        log.info("[agent.browser] exec: {}", String.join(" ", cmdLine));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdLine);
            pb.redirectErrorStream(true);   // stderr 合并到 stdout
            pb.environment().put("FORCE_COLOR", "0"); // 关闭 ANSI 颜色，避免乱码

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(call.id(), false, Map.of("output", output.toString()),
                        "agent-browser 命令执行超时（" + timeoutMs + "ms）");
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exitCode", exitCode);
            result.put("output", outputStr);
            result.put("command", String.join(" ", cmdLine));
            result.put("success", exitCode == 0);

            if (exitCode != 0) {
                log.warn("[agent.browser] exit={}, output={}", exitCode, outputStr);
                return new ToolResult(call.id(), false, result,
                        "命令返回非零退出码 " + exitCode + "，输出：" + outputStr);
            }

            return new ToolResult(call.id(), true, result, null);

        } catch (Exception e) {
            log.error("[agent.browser] exec failed, cmd={}", String.join(" ", cmdLine), e);
            String msg = e.getMessage();
            if (msg != null && (msg.contains("No such file") || msg.contains("cannot find"))) {
                msg = "找不到 agent-browser 命令，请先执行：npm install -g agent-browser && agent-browser install";
            }
            return new ToolResult(call.id(), false, Map.of("error", String.valueOf(e.getMessage())), msg);
        }
    }

    /**
     * 将命令字符串按空格分词，但保留双引号内的内容（含空格）。
     * 例：{@code fill @e1 "hello world"} → {@code ["fill", "@e1", "hello world"]}
     */
    private List<String> splitCommand(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ' ' && !inQuote) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String argString(Map<String, Object> args, String key) {
        if (args == null || key == null) return null;
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private boolean argBool(Map<String, Object> args, String key, boolean def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private int argInt(Map<String, Object> args, String key, int def, int min, int max) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        try {
            int parsed = Integer.parseInt(String.valueOf(v));
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
