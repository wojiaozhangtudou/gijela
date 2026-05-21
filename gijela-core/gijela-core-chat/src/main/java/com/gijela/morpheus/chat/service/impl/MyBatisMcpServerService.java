package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.domain.dto.McpServerSaveRequest;
import com.gijela.morpheus.chat.domain.entity.ChatMcpServer;
import com.gijela.morpheus.chat.domain.vo.McpServerDetailVO;
import com.gijela.morpheus.chat.domain.vo.McpServerSummaryVO;
import com.gijela.morpheus.chat.mapper.ChatMcpServerMapper;
import com.gijela.morpheus.chat.service.McpServerService;
import com.gijela.morpheus.chat.util.UrlGuard;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.llm.sdk.mcp.McpSdkProperties;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpInitializeResult;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpSkillSync;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBindingSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyBatisMcpServerService implements McpServerService, McpToolBindingSource {

    private static final Logger log = LoggerFactory.getLogger(MyBatisMcpServerService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatMcpServerMapper mapper;
    private final McpJsonRpcClient mcpClient;
    private final McpSdkProperties sdkProperties;
    private final ObjectProvider<McpSkillSync> skillSyncProvider;

    public MyBatisMcpServerService(ChatMcpServerMapper mapper,
                                   McpJsonRpcClient mcpClient,
                                   McpSdkProperties sdkProperties,
                                   ObjectProvider<McpSkillSync> skillSyncProvider) {
        this.mapper = mapper;
        this.mcpClient = mcpClient;
        this.sdkProperties = sdkProperties;
        this.skillSyncProvider = skillSyncProvider;
    }

    private void triggerSkillSync(String reason) {
        if (skillSyncProvider == null) return;
        try {
            McpSkillSync sync = skillSyncProvider.getIfAvailable();
            if (sync == null) return;
            int n = sync.syncAll();
            log.info("[mcp-skill] re-sync triggered by {}, exposed={}", reason, n);
        } catch (Exception e) {
            log.warn("[mcp-skill] re-sync failed (reason={}): {}", reason, e.getMessage());
        }
    }

    @Override
    public List<McpServerSummaryVO> list(String tenantId) {
        String t = normalize(tenantId);
        List<ChatMcpServer> rows = mapper.selectList(
                new QueryWrapper<ChatMcpServer>().eq("tenant_id", t).orderByDesc("updated_at"));
        return rows.stream().map(this::toSummary).toList();
    }

    @Override
    public McpServerDetailVO detail(String tenantId, String name) {
        return toDetail(loadOrThrow(tenantId, name));
    }

    @Override
    public synchronized McpServerDetailVO create(String tenantId, McpServerSaveRequest req, String operator) {
        String t = normalize(tenantId);
        validate(req);
        if (findByName(t, req.getName()) != null) {
            throw new BizException(ErrorCode.CONFLICT, "MCP Server 名称已存在: " + req.getName());
        }
        ChatMcpServer row = new ChatMcpServer();
        row.setTenantId(t);
        row.setName(req.getName());
        applyRequest(row, req, true, operator);
        row.setStatus("unknown");
        row.setStatusMessage(null);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedBy(operator);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
        log.info("[mcp-admin] create name={}, transport={}, target={}",
                row.getName(), row.getTransport(), endpointOrCommand(row));
        triggerSkillSync("create:" + row.getName());
        return toDetail(row);
    }

    @Override
    public synchronized McpServerDetailVO update(String tenantId, String name,
                                                 McpServerSaveRequest req, String operator) {
        validate(req);
        ChatMcpServer row = loadOrThrow(tenantId, name);
        if (!row.getName().equals(req.getName())) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "name 不可修改");
        }
        applyRequest(row, req, false, operator);
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        log.info("[mcp-admin] update name={}", name);
        triggerSkillSync("update:" + name);
        return toDetail(row);
    }

    @Override
    public synchronized void delete(String tenantId, String name) {
        ChatMcpServer row = loadOrThrow(tenantId, name);
        mapper.deleteById(row.getId());
        log.info("[mcp-admin] delete name={}", name);
        triggerSkillSync("delete:" + name);
    }

    @Override
    public synchronized McpServerDetailVO toggle(String tenantId, String name,
                                                 boolean enabled, String operator) {
        ChatMcpServer row = loadOrThrow(tenantId, name);
        row.setEnabled(enabled);
        row.setUpdatedBy(operator);
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        log.info("[mcp-admin] toggle name={}, enabled={}", name, enabled);
        triggerSkillSync("toggle:" + name + "=" + enabled);
        return toDetail(row);
    }

    @Override
    public synchronized Map<String, Object> test(String tenantId, String name) {
        ChatMcpServer row = loadOrThrow(tenantId, name);
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            McpInitializeResult init = mcpClient.initialize(toEndpointConfig(row));
            row.setStatus("ok");
            row.setStatusMessage("connected: " + nullToDash(init.serverName())
                    + "/" + nullToDash(init.serverVersion()));
            resp.put("ok", true);
            resp.put("serverInfo", Map.of(
                    "name", nullToDash(init.serverName()),
                    "version", nullToDash(init.serverVersion()),
                    "protocolVersion", nullToDash(init.protocolVersion())));
            resp.put("capabilities", init.capabilities());
            resp.put("message", row.getStatusMessage());
        } catch (Exception e) {
            row.setStatus("error");
            row.setStatusMessage(clip(e.getMessage(), 1000));
            resp.put("ok", false);
            resp.put("message", e.getMessage());
            log.warn("[mcp-admin] test failed name={}, err={}", name, e.getMessage());
        }
        row.setLastTestedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        return resp;
    }

    @Override
    public synchronized McpServerDetailVO refreshTools(String tenantId, String name) {
        ChatMcpServer row = loadOrThrow(tenantId, name);
        try {
            List<Map<String, Object>> tools = mcpClient.listTools(toEndpointConfig(row));
            row.setToolsCacheJson(MAPPER.writeValueAsString(tools));
            row.setStatus("ok");
            row.setStatusMessage("tools=" + tools.size());
        } catch (McpClientException e) {
            row.setStatus("error");
            row.setStatusMessage(clip("tools/list 失败: " + e.getMessage(), 1000));
            throw new BizException(ErrorCode.NETWORK_IO_ERROR, e.getMessage());
        } catch (Exception e) {
            row.setStatus("error");
            row.setStatusMessage(clip("tools/list 失败: " + e.getMessage(), 1000));
            throw new BizException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            row.setLastTestedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(row);
        }
        log.info("[mcp-admin] refreshTools name={}", name);
        triggerSkillSync("refreshTools:" + name);
        return toDetail(row);
    }

    @Override
    public List<McpToolBinding> loadActiveBindings(String tenantId) {
        QueryWrapper<ChatMcpServer> qw = new QueryWrapper<ChatMcpServer>()
                .eq("enabled", 1)
                .eq("status", "ok")
                .isNotNull("tools_cache_json");
        if (tenantId != null && !tenantId.isBlank()) {
            qw.eq("tenant_id", tenantId);
        }
        List<ChatMcpServer> rows = mapper.selectList(qw);
        List<McpToolBinding> out = new java.util.ArrayList<>();
        int maxTotal = sdkProperties.getMaxToolsExposed();
        for (ChatMcpServer row : rows) {
            List<Map<String, Object>> tools;
            try {
                tools = MAPPER.readValue(row.getToolsCacheJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("[mcp-skill] parse tools_cache_json failed: name={}, err={}",
                        row.getName(), e.getMessage());
                continue;
            }
            McpEndpointConfig endpoint;
            try {
                endpoint = toEndpointConfig(row);
            } catch (Exception e) {
                log.warn("[mcp-skill] build endpoint config failed: name={}, err={}",
                        row.getName(), e.getMessage());
                continue;
            }
            for (Map<String, Object> tool : tools) {
                if (out.size() >= maxTotal) {
                    log.warn("[mcp-skill] reach maxToolsExposed={}, drop remaining tools", maxTotal);
                    return out;
                }
                String original = String.valueOf(tool.get("name"));
                if (original == null || original.isBlank() || "null".equals(original)) continue;
                String fqName = sanitizeToolName(row.getName(), original);
                if (fqName == null) continue;
                String desc = tool.get("description") == null ? "" : String.valueOf(tool.get("description"));
                @SuppressWarnings("unchecked")
                Map<String, Object> inputSchema = tool.get("inputSchema") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of();
                out.add(new McpToolBinding(fqName, row.getName(), original, desc, inputSchema, endpoint));
            }
        }
        return out;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 8) return "***";
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * 把 ({@code serverName}, {@code toolName}) 拼成符合 OpenAI 函数命名规范的全限定名：
     * {@code mcp__<server>__<tool>}，仅保留 [a-zA-Z0-9_-]，超长截断到64字符。
     */
    static String sanitizeToolName(String serverName, String toolName) {
        if (serverName == null || toolName == null) return null;
        String s = serverName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String t = toolName.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (s.isBlank() || t.isBlank()) return null;
        String full = "mcp__" + s + "__" + t;
        if (full.length() > 64) {
            // 末尾保留 tool 名信息：截断 server 段
            int over = full.length() - 64;
            int newServerLen = Math.max(1, s.length() - over);
            full = "mcp__" + s.substring(0, newServerLen) + "__" + t;
            if (full.length() > 64) full = full.substring(0, 64);
        }
        return full;
    }

    // ---------------------- 私有 ----------------------

    private void validate(McpServerSaveRequest req) {
        String transport = normalizeTransport(req.getTransport());
        switch (transport) {
            case "streamable_http", "sse" -> {
                if (req.getEndpoint() == null || req.getEndpoint().isBlank()) {
                    throw new BizException(ErrorCode.INVALID_ARGUMENT,
                            "transport=" + transport + " 必须提供 endpoint");
                }
                UrlGuard.validate(req.getEndpoint(), true);
            }
            case "stdio" -> {
                if (!sdkProperties.isAllowStdio()) {
                    throw new BizException(ErrorCode.INVALID_ARGUMENT,
                            "stdio transport 已被禁用，请联系管理员开启 gijela.llm.mcp.allow-stdio");
                }
                if (req.getCommand() == null || req.getCommand().isBlank()) {
                    throw new BizException(ErrorCode.INVALID_ARGUMENT, "stdio transport 必须提供 command");
                }
                List<String> whitelist = sdkProperties.getStdioCommandWhitelist();
                if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(req.getCommand())) {
                    throw new BizException(ErrorCode.INVALID_ARGUMENT,
                            "stdio command 不在白名单内: " + req.getCommand());
                }
            }
            default -> throw new BizException(ErrorCode.INVALID_ARGUMENT,
                    "不支持的 transport: " + req.getTransport());
        }
    }

    private void applyRequest(ChatMcpServer row, McpServerSaveRequest req,
                              boolean isCreate, String operator) {
        String transport = normalizeTransport(req.getTransport());
        row.setDisplayName(req.getDisplayName());
        row.setDescription(req.getDescription());
        row.setTransport(transport);
        row.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        row.setUpdatedBy(operator);

        if ("stdio".equals(transport)) {
            row.setEndpoint(null);
            row.setCommand(req.getCommand());
            row.setArgsJson(writeJsonOrNull(req.getArgs()));
            row.setEnvJson(writeJsonOrNull(req.getEnv()));
            row.setWorkingDir(req.getWorkingDir());
            // stdio 不使用 token 鉴权
            row.setAuthType("none");
            row.setAuthTokenCipher(null);
            return;
        }

        // streamable_http / sse
        row.setEndpoint(req.getEndpoint());
        row.setCommand(null);
        row.setArgsJson(null);
        row.setEnvJson(null);
        row.setWorkingDir(null);
        row.setAuthType(req.getAuthType());

        if ("none".equalsIgnoreCase(req.getAuthType())) {
            row.setAuthTokenCipher(null);
        } else if ("bearer".equalsIgnoreCase(req.getAuthType())) {
            if (req.getAuthToken() == null) {
                if (isCreate) {
                    throw new BizException(ErrorCode.INVALID_ARGUMENT, "bearer 鉴权必须提供 authToken");
                }
                // 编辑时保持原 cipher 不变
            } else if (req.getAuthToken().isEmpty()) {
                row.setAuthTokenCipher(null);
            } else {
                row.setAuthTokenCipher(req.getAuthToken());
            }
        }
    }

    private McpEndpointConfig toEndpointConfig(ChatMcpServer row) {
        String transport = normalizeTransport(row.getTransport());
        if ("stdio".equals(transport)) {
            return McpEndpointConfig.stdio(
                    row.getCommand(),
                    readJsonList(row.getArgsJson()),
                    readJsonStringMap(row.getEnvJson()),
                    row.getWorkingDir());
        }
        String token = decryptToken(row);
        if ("sse".equals(transport)) {
            return McpEndpointConfig.sse(row.getEndpoint(), token);
        }
        return McpEndpointConfig.http(row.getEndpoint(), token);
    }

    private static String normalizeTransport(String t) {
        if (t == null) return "streamable_http";
        String k = t.toLowerCase(Locale.ROOT);
        return "http".equals(k) ? "streamable_http" : k;
    }

    private ChatMcpServer loadOrThrow(String tenantId, String name) {
        ChatMcpServer row = findByName(normalize(tenantId), name);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP Server 不存在: " + name);
        }
        return row;
    }

    private ChatMcpServer findByName(String tenantId, String name) {
        return mapper.selectOne(new QueryWrapper<ChatMcpServer>()
                .eq("tenant_id", tenantId).eq("name", name).last("LIMIT 1"));
    }

    private String decryptToken(ChatMcpServer row) {
        if (row.getAuthTokenCipher() == null || row.getAuthTokenCipher().isBlank()) return null;
        return row.getAuthTokenCipher();
    }

    private McpServerSummaryVO toSummary(ChatMcpServer row) {
        Integer toolCount = null;
        if (row.getToolsCacheJson() != null) {
            try {
                List<?> tools = MAPPER.readValue(row.getToolsCacheJson(), new TypeReference<List<?>>() {});
                toolCount = tools.size();
            } catch (Exception ignore) {
                toolCount = null;
            }
        }
        return new McpServerSummaryVO(
                row.getId(),
                row.getName(),
                row.getDisplayName(),
                row.getDescription(),
                row.getTransport(),
                row.getEndpoint(),
                row.getCommand(),
                endpointOrCommand(row),
                row.getAuthType(),
                row.getAuthTokenCipher() != null && !row.getAuthTokenCipher().isBlank(),
                row.getEnabled() == null || row.getEnabled(),
                row.getStatus(),
                row.getStatusMessage(),
                toolCount,
                row.getLastTestedAt(),
                row.getUpdatedAt()
        );
    }

    private McpServerDetailVO toDetail(ChatMcpServer row) {
        List<Map<String, Object>> tools = List.of();
        if (row.getToolsCacheJson() != null) {
            try {
                tools = MAPPER.readValue(row.getToolsCacheJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception ignore) {
                tools = List.of();
            }
        }
        String mask = null;
        if (row.getAuthTokenCipher() != null && !row.getAuthTokenCipher().isBlank()) {
            mask = maskToken(row.getAuthTokenCipher());
        }
        return new McpServerDetailVO(
                row.getId(),
                row.getName(),
                row.getDisplayName(),
                row.getDescription(),
                row.getTransport(),
                row.getEndpoint(),
                row.getCommand(),
                readJsonList(row.getArgsJson()),
                readJsonStringMap(row.getEnvJson()),
                row.getWorkingDir(),
                row.getAuthType(),
                mask,
                row.getEnabled() == null || row.getEnabled(),
                row.getStatus(),
                row.getStatusMessage(),
                tools,
                row.getLastTestedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private static String endpointOrCommand(ChatMcpServer row) {
        if ("stdio".equalsIgnoreCase(normalizeTransport(row.getTransport()))) {
            return row.getCommand();
        }
        return row.getEndpoint();
    }

    private String writeJsonOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof java.util.Collection<?> c && c.isEmpty()) return null;
        if (v instanceof Map<?, ?> m && m.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(v);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "字段序列化失败: " + e.getMessage());
        }
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[mcp-admin] parse args_json failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> readJsonStringMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("[mcp-admin] parse env_json failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String normalize(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
