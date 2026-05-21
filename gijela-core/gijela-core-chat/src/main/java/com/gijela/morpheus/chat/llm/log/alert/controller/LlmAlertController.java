package com.gijela.morpheus.chat.llm.log.alert.controller;

import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertEventActionRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertEventPageRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertRuleToggleRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertRuleUpsertRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.response.AlertEventVO;
import com.gijela.morpheus.chat.llm.log.alert.dto.response.AlertRuleVO;
import com.gijela.morpheus.chat.llm.log.alert.service.LlmAlertService;
import com.gijela.morpheus.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llm-logs/alerts")
@Tag(name = "LLM 日志告警", description = "LLM 日志告警规则与事件接口")
@SecurityRequirement(name = "Bearer Authentication")
public class LlmAlertController {

    private final LlmAlertService llmAlertService;

    public LlmAlertController(LlmAlertService llmAlertService) {
        this.llmAlertService = llmAlertService;
    }

    @GetMapping("/rules")
    @Operation(summary = "告警规则列表")
    public ApiResponse<List<AlertRuleVO>> listRules(HttpServletRequest request) {
        return ApiResponse.ok(llmAlertService.listRules(tenantOf(request)));
    }

    @PostMapping("/rules")
    @Operation(summary = "创建告警规则")
    public ApiResponse<Long> createRule(@Valid @RequestBody AlertRuleUpsertRequest request,
                                        HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(llmAlertService.createRule(tenantOf(httpServletRequest), operatorOf(httpServletRequest), request));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新告警规则")
    public ApiResponse<Long> updateRule(@PathVariable Long id,
                                        @Valid @RequestBody AlertRuleUpsertRequest request,
                                        HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(llmAlertService.updateRule(id, tenantOf(httpServletRequest), operatorOf(httpServletRequest), request));
    }

    @PostMapping("/rules/{id}/toggle")
    @Operation(summary = "启停告警规则")
    public ApiResponse<Void> toggleRule(@PathVariable Long id,
                                        @Valid @RequestBody AlertRuleToggleRequest request,
                                        HttpServletRequest httpServletRequest) {
        llmAlertService.toggleRule(id, tenantOf(httpServletRequest), operatorOf(httpServletRequest), Boolean.TRUE.equals(request.getEnabled()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/rules/{id}/trigger")
    @Operation(summary = "手动触发规则评估")
    public ApiResponse<Long> triggerRule(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(llmAlertService.triggerRule(id, tenantOf(request), operatorOf(request)));
    }

    @PostMapping("/events/page")
    @Operation(summary = "分页查询告警事件")
    public ApiResponse<Page<AlertEventVO>> pageEvents(@RequestBody(required = false) AlertEventPageRequest request,
                                                      HttpServletRequest httpServletRequest) {
        AlertEventPageRequest actualRequest = request == null ? new AlertEventPageRequest() : request;
        if (actualRequest.getTenantId() == null || actualRequest.getTenantId().isBlank()) {
            actualRequest.setTenantId(tenantOf(httpServletRequest));
        }
        return ApiResponse.ok(llmAlertService.pageEvents(actualRequest));
    }

    @PostMapping("/events/{id}/ack")
    @Operation(summary = "确认告警事件")
    public ApiResponse<Void> ackEvent(@PathVariable Long id,
                                      @RequestBody(required = false) AlertEventActionRequest request,
                                      HttpServletRequest httpServletRequest) {
        llmAlertService.ackEvent(id, tenantOf(httpServletRequest), request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/events/{id}/resolve")
    @Operation(summary = "关闭告警事件")
    public ApiResponse<Void> resolveEvent(@PathVariable Long id,
                                          @RequestBody(required = false) AlertEventActionRequest request,
                                          HttpServletRequest httpServletRequest) {
        llmAlertService.resolveEvent(id, tenantOf(httpServletRequest), request);
        return ApiResponse.ok(null);
    }

    private String tenantOf(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private String operatorOf(HttpServletRequest request) {
        String operator = request.getHeader("X-Operator");
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}
