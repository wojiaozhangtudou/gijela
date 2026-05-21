package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptItemCreateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptItemUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptItemCreateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptItemUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptDraftUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptPublishRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptRollbackRequest;
import com.gijela.morpheus.chat.domain.vo.prompt.PromptItemResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SessionPromptResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SystemPromptCurrentResponse;
import com.gijela.morpheus.chat.service.PromptConfigService;
import com.gijela.morpheus.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/chat")
public class ChatPromptController {

    private final PromptConfigService promptConfigService;
    private final ChatModuleProperties properties;

    public ChatPromptController(PromptConfigService promptConfigService, ChatModuleProperties properties) {
        this.promptConfigService = promptConfigService;
        this.properties = properties;
    }

    @GetMapping("/prompts/system/current")
    public ApiResponse<SystemPromptCurrentResponse> getSystemCurrent(@RequestParam("appCode") String appCode,
                                                                     @RequestParam("modelRoute") String modelRoute,
                                                                     HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.getSystemCurrent(resolveTenant(request), appCode, modelRoute));
    }

    @GetMapping("/sessions/{sessionId}/prompt")
    public ApiResponse<SessionPromptResponse> getSessionPrompt(@PathVariable("sessionId") String sessionId,
                                                               HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.getSessionPrompt(resolveTenant(request), sessionId));
    }

    @GetMapping("/prompt-items/system")
    public ApiResponse<List<PromptItemResponse>> listSystemPromptItems(@RequestParam("appCode") String appCode,
                                                                        @RequestParam("modelRoute") String modelRoute,
                                                                        HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.listSystemPromptItems(resolveTenant(request), appCode, modelRoute));
    }

    @PostMapping("/prompt-items/system")
    public ApiResponse<PromptItemResponse> createSystemPromptItem(@Valid @RequestBody SystemPromptItemCreateRequest body,
                                                                   HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.createSystemPromptItem(resolveTenant(request), resolveOperator(request), body));
    }

    @PutMapping("/prompt-items/system/{id}")
    public ApiResponse<PromptItemResponse> updateSystemPromptItem(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody SystemPromptItemUpdateRequest body,
                                                                   HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.updateSystemPromptItem(resolveTenant(request), id, resolveOperator(request), body));
    }

    @DeleteMapping("/prompt-items/system/{id}")
    public ApiResponse<Void> deleteSystemPromptItem(@PathVariable("id") Long id,
                                                     HttpServletRequest request) {
        promptConfigService.deleteSystemPromptItem(resolveTenant(request), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/prompt-items/sessions/{sessionId}")
    public ApiResponse<List<PromptItemResponse>> listSessionPromptItems(@PathVariable("sessionId") String sessionId,
                                                                         HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.listSessionPromptItems(resolveTenant(request), sessionId));
    }

    @PostMapping("/prompt-items/sessions/{sessionId}")
    public ApiResponse<PromptItemResponse> createSessionPromptItem(@PathVariable("sessionId") String sessionId,
                                                                    @Valid @RequestBody SessionPromptItemCreateRequest body,
                                                                    HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.createSessionPromptItem(resolveTenant(request), sessionId, resolveOperator(request), body));
    }

    @PutMapping("/prompt-items/sessions/{sessionId}/{id}")
    public ApiResponse<PromptItemResponse> updateSessionPromptItem(@PathVariable("sessionId") String sessionId,
                                                                    @PathVariable("id") Long id,
                                                                    @Valid @RequestBody SessionPromptItemUpdateRequest body,
                                                                    HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.updateSessionPromptItem(resolveTenant(request), sessionId, id, resolveOperator(request), body));
    }

    @DeleteMapping("/prompt-items/sessions/{sessionId}/{id}")
    public ApiResponse<Void> deleteSessionPromptItem(@PathVariable("sessionId") String sessionId,
                                                      @PathVariable("id") Long id,
                                                      HttpServletRequest request) {
        promptConfigService.deleteSessionPromptItem(resolveTenant(request), sessionId, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/prompts/system/draft")
    public ApiResponse<SystemPromptCurrentResponse> saveSystemDraft(@Valid @RequestBody SystemPromptDraftUpdateRequest body,
                                                                    HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.saveSystemDraft(resolveTenant(request), resolveOperator(request), body));
    }

    @PostMapping("/prompts/system/publish")
    public ApiResponse<SystemPromptCurrentResponse> publishSystemPrompt(@Valid @RequestBody SystemPromptPublishRequest body,
                                                                        HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.publishSystemPrompt(resolveTenant(request), resolveOperator(request), body));
    }

    @PostMapping("/prompts/system/rollback")
    public ApiResponse<SystemPromptCurrentResponse> rollbackSystemPrompt(@Valid @RequestBody SystemPromptRollbackRequest body,
                                                                         HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.rollbackSystemPrompt(resolveTenant(request), resolveOperator(request), body));
    }

    @PutMapping("/sessions/{sessionId}/prompt")
    public ApiResponse<SessionPromptResponse> updateSessionPrompt(@PathVariable("sessionId") String sessionId,
                                                                  @Valid @RequestBody SessionPromptUpdateRequest body,
                                                                  HttpServletRequest request) {
        return ApiResponse.ok(promptConfigService.updateSessionPrompt(resolveTenant(request), sessionId, resolveOperator(request), body));
    }

    private String resolveTenant(HttpServletRequest request) {
        String tenant = request.getHeader(properties.getServer().getTenantHeader());
        return (tenant == null || tenant.isBlank()) ? "default" : tenant;
    }

    private String resolveOperator(HttpServletRequest request) {
        String operator = request.getHeader("X-Operator");
        return (operator == null || operator.isBlank()) ? "unknown" : operator;
    }
}
