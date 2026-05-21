package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.model.ModelConfigSaveRequest;
import com.gijela.morpheus.chat.domain.vo.model.ModelConfigItemResponse;
import com.gijela.morpheus.chat.domain.vo.model.ModelConfigOptionResponse;
import com.gijela.morpheus.chat.service.ModelConfigService;
import com.gijela.morpheus.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/chat/model-configs")
public class ChatModelConfigController {

    private final ModelConfigService modelConfigService;
    private final ChatModuleProperties properties;

    public ChatModelConfigController(ModelConfigService modelConfigService, ChatModuleProperties properties) {
        this.modelConfigService = modelConfigService;
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<List<ModelConfigItemResponse>> list(@RequestParam(value = "configType", required = false) String configType,
                                                            HttpServletRequest request) {
        return ApiResponse.ok(modelConfigService.list(resolveTenant(request), configType));
    }

    @GetMapping("/options")
    public ApiResponse<List<ModelConfigOptionResponse>> listOptions(@RequestParam("configType") String configType,
                                                                     HttpServletRequest request) {
        return ApiResponse.ok(modelConfigService.listOptions(resolveTenant(request), configType));
    }

    @PostMapping
    public ApiResponse<ModelConfigItemResponse> create(@Valid @RequestBody ModelConfigSaveRequest request,
                                                        HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(modelConfigService.create(resolveTenant(httpServletRequest), resolveOperator(httpServletRequest), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigItemResponse> update(@PathVariable("id") Long id,
                                                        @Valid @RequestBody ModelConfigSaveRequest request,
                                                        HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(modelConfigService.update(resolveTenant(httpServletRequest), id, resolveOperator(httpServletRequest), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id,
                                    HttpServletRequest request) {
        modelConfigService.delete(resolveTenant(request), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable("id") Long id,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(modelConfigService.testConnection(resolveTenant(request), id));
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
