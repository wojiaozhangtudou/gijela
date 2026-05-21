package com.gijela.morpheus.llm.sdk.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP SDK 配置项。前缀：{@code gijela.llm.mcp}
 *
 * <p>注意：业务侧（chat）专属的 {@code cipherKey} / {@code allowPrivateNetwork}
 * 仍保留在业务自己的 properties 中，本类只承载与 SDK transport / sync 相关的字段。</p>
 */
@ConfigurationProperties(prefix = "gijela.llm.mcp")
public class McpSdkProperties {

    /** MCP HTTP 客户端连接超时（秒）。 */
    private int connectTimeoutSeconds = 5;

    /** MCP HTTP 客户端读取超时（秒）。 */
    private int readTimeoutSeconds = 30;

    /** MCP HTTP 客户端整体调用超时（秒）。 */
    private int callTimeoutSeconds = 30;

    /** 是否允许 stdio transport（默认 false）。 */
    private boolean allowStdio = false;

    /** stdio 命令白名单：为空表示放行任意命令（仅当 allowStdio=true）。 */
    private List<String> stdioCommandWhitelist = new ArrayList<>();

    /** stdio 子进程启动后的检活窗口（秒）。 */
    private int stdioStartupTimeoutSeconds = 30;

    /** stdio 单次 JSON-RPC 调用超时（秒）。 */
    private int stdioCallTimeoutSeconds = 60;

    /** 暴露给 LLM 的 MCP tool 总数上限（防 prompt 爆炸）。 */
    private int maxToolsExposed = 64;

    /** 单次 MCP tool_call 回灌 LLM 的最大字符数（防 token 暴涨）。 */
    private int maxToolResponseChars = 25000;

    /** 是否在启动时自动同步 MCP 工具到 SkillRegistry。 */
    private boolean autoExposeToSkills = true;

    /** Cron 表达式：定期重新同步 MCP 工具；空字符串=不调度（默认）。 */
    private String syncCron = "";

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int v) { this.connectTimeoutSeconds = v; }

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int v) { this.readTimeoutSeconds = v; }

    public int getCallTimeoutSeconds() { return callTimeoutSeconds; }
    public void setCallTimeoutSeconds(int v) { this.callTimeoutSeconds = v; }

    public boolean isAllowStdio() { return allowStdio; }
    public void setAllowStdio(boolean v) { this.allowStdio = v; }

    public List<String> getStdioCommandWhitelist() { return stdioCommandWhitelist; }
    public void setStdioCommandWhitelist(List<String> v) { this.stdioCommandWhitelist = v; }

    public int getStdioStartupTimeoutSeconds() { return stdioStartupTimeoutSeconds; }
    public void setStdioStartupTimeoutSeconds(int v) { this.stdioStartupTimeoutSeconds = v; }

    public int getStdioCallTimeoutSeconds() { return stdioCallTimeoutSeconds; }
    public void setStdioCallTimeoutSeconds(int v) { this.stdioCallTimeoutSeconds = v; }

    public int getMaxToolsExposed() { return maxToolsExposed; }
    public void setMaxToolsExposed(int v) { this.maxToolsExposed = v; }

    public int getMaxToolResponseChars() { return maxToolResponseChars; }
    public void setMaxToolResponseChars(int v) { this.maxToolResponseChars = v; }

    public boolean isAutoExposeToSkills() { return autoExposeToSkills; }
    public void setAutoExposeToSkills(boolean v) { this.autoExposeToSkills = v; }

    public String getSyncCron() { return syncCron; }
    public void setSyncCron(String v) { this.syncCron = v; }
}
