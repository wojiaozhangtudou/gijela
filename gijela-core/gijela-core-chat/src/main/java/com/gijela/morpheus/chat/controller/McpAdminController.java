package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.McpServerSaveRequest;
import com.gijela.morpheus.chat.domain.vo.McpServerDetailVO;
import com.gijela.morpheus.chat.domain.vo.McpServerSummaryVO;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.chat.service.McpServerService;
import com.gijela.morpheus.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 管理接口（前缀 /api/v1/chat/admin/mcp-servers）。
 *
 * <p>阶段 A：CRUD + toggle + test + refresh-tools，所有写操作落 chat_audit_log。</p>
 */
@RestController
@RequestMapping("/api/v1/chat/admin/mcp-servers")
public class McpAdminController {

    private static final Logger log = LoggerFactory.getLogger(McpAdminController.class);

    private final McpServerService service;
    private final ChatAuditService auditService;
    private final ChatModuleProperties properties;

    public McpAdminController(McpServerService service,
                              ChatAuditService auditService,
                              ChatModuleProperties properties) {
        this.service = service;
        this.auditService = auditService;
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<List<McpServerSummaryVO>> list(HttpServletRequest req) {
        return ApiResponse.ok(service.list(tenantOf(req)));
    }

    @GetMapping("/{name}")
    public ApiResponse<McpServerDetailVO> detail(@PathVariable String name, HttpServletRequest req) {
        return ApiResponse.ok(service.detail(tenantOf(req), name));
    }

    @PostMapping
    public ApiResponse<McpServerDetailVO> create(@Valid @RequestBody McpServerSaveRequest body,
                                                 HttpServletRequest req) {
        String tenant = tenantOf(req);
        McpServerDetailVO vo = service.create(tenant, body, operatorOf(req));
        audit(req, tenant, "mcp.create", "name=" + body.getName());
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{name}")
    public ApiResponse<McpServerDetailVO> update(@PathVariable String name,
                                                 @Valid @RequestBody McpServerSaveRequest body,
                                                 HttpServletRequest req) {
        String tenant = tenantOf(req);
        McpServerDetailVO vo = service.update(tenant, name, body, operatorOf(req));
        audit(req, tenant, "mcp.update", "name=" + name);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Boolean> delete(@PathVariable String name, HttpServletRequest req) {
        String tenant = tenantOf(req);
        service.delete(tenant, name);
        audit(req, tenant, "mcp.delete", "name=" + name);
        return ApiResponse.ok(Boolean.TRUE);
    }

    @PostMapping("/{name}/toggle")
    public ApiResponse<McpServerDetailVO> toggle(@PathVariable String name,
                                                 @Valid @RequestBody ToggleRequest body,
                                                 HttpServletRequest req) {
        String tenant = tenantOf(req);
        McpServerDetailVO vo = service.toggle(tenant, name, body.enabled, operatorOf(req));
        audit(req, tenant, "mcp.toggle", "name=" + name + ", enabled=" + body.enabled);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/{name}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String name, HttpServletRequest req) {
        String tenant = tenantOf(req);
        Map<String, Object> r = service.test(tenant, name);
        audit(req, tenant, "mcp.test", "name=" + name + ", ok=" + r.get("ok"));
        return ApiResponse.ok(r);
    }

    @PostMapping("/{name}/refresh-tools")
    public ApiResponse<McpServerDetailVO> refreshTools(@PathVariable String name, HttpServletRequest req) {
        String tenant = tenantOf(req);
        McpServerDetailVO vo = service.refreshTools(tenant, name);
        audit(req, tenant, "mcp.refresh-tools", "name=" + name + ", tools=" + vo.tools().size());
        return ApiResponse.ok(vo);
    }

    // ---------------- helpers ----------------

    private String tenantOf(HttpServletRequest req) {
        String h = req.getHeader(properties.getServer().getTenantHeader());
        return (h == null || h.isBlank()) ? "default" : h;
    }

    private String operatorOf(HttpServletRequest req) {
        String h = req.getHeader("X-Operator");
        return (h == null || h.isBlank()) ? "system" : h;
    }

    private void audit(HttpServletRequest req, String tenant, String action, String result) {
        try {
            auditService.record(tenant,
                    req.getHeader(properties.getServer().getRequestHeader()),
                    null, action, result);
        } catch (Exception e) {
            log.warn("[mcp-admin] audit failed action={}, err={}", action, e.getMessage());
        }
    }

    public static class ToggleRequest {
        @NotNull
        public Boolean enabled;
    }
}
