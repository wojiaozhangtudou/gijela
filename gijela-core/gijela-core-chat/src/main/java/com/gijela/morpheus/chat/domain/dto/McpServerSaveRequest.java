package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 新增/编辑请求体。
 *
 * <p>三种 transport 字段约束：</p>
 * <ul>
 *   <li>{@code streamable_http} / {@code sse}：必须提供 {@code endpoint}；忽略 stdio 字段。</li>
 *   <li>{@code stdio}：必须提供 {@code command}；忽略 endpoint / authType / authToken。</li>
 * </ul>
 *
 * <p>编辑时若 {@code authToken} 为 null 表示不改动，空串表示清空。</p>
 */
public class McpServerSaveRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$",
            message = "name 只能小写字母/数字/短横线，长度 3-64")
    private String name;

    @NotBlank
    @Size(max = 128)
    private String displayName;

    @Size(max = 512)
    private String description;

    /** streamable_http / sse / stdio；为兼容历史值 http 也接受，服务端归一为 streamable_http。 */
    @NotBlank
    @Pattern(regexp = "^(streamable_http|http|sse|stdio)$",
            message = "transport 仅支持 streamable_http | sse | stdio")
    private String transport = "streamable_http";

    /** http / sse 必填；stdio 忽略。 */
    @Size(max = 512)
    private String endpoint;

    /** stdio 必填。 */
    @Size(max = 256)
    private String command;

    /** stdio 启动参数列表。 */
    private List<String> args;

    /** stdio 子进程附加环境变量。 */
    private Map<String, String> env;

    /** stdio 子进程工作目录。 */
    @Size(max = 512)
    private String workingDir;

    @NotBlank
    @Pattern(regexp = "^(none|bearer)$", message = "authType 仅支持 none | bearer")
    private String authType = "none";

    /** null = 编辑时保持原值；非 null = 覆盖（空串视为清空）。仅 http/sse 生效。 */
    @Size(max = 1024)
    private String authToken;

    private Boolean enabled = Boolean.TRUE;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args; }

    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
