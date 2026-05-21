package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.adapter.skill.SkillHotReloadScheduler;
import com.gijela.morpheus.llm.sdk.skill.SkillManifest;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import com.gijela.morpheus.llm.sdk.skill.SkillSource;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.entity.ChatSkillState;
import com.gijela.morpheus.chat.domain.vo.SkillDetailVO;
import com.gijela.morpheus.chat.domain.vo.SkillSummaryVO;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.chat.service.SkillStateService;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能管理接口（管理后台用，前缀 /api/v1/chat/admin/skills）。
 *
 * <p>阶段 A：list / detail / toggle / reload，写操作走 chat_audit_log 审计。</p>
 */
@RestController
@RequestMapping("/api/v1/chat/admin/skills")
public class SkillAdminController {

    private static final Logger log = LoggerFactory.getLogger(SkillAdminController.class);

    private final SkillRegistry skillRegistry;
    private final SkillStateService skillStateService;
    private final SkillHotReloadScheduler hotReloadScheduler;
    private final ChatAuditService chatAuditService;
    private final ChatModuleProperties properties;

    public SkillAdminController(SkillRegistry skillRegistry,
                                SkillStateService skillStateService,
                                SkillHotReloadScheduler hotReloadScheduler,
                                ChatAuditService chatAuditService,
                                ChatModuleProperties properties) {
        this.skillRegistry = skillRegistry;
        this.skillStateService = skillStateService;
        this.hotReloadScheduler = hotReloadScheduler;
        this.chatAuditService = chatAuditService;
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<List<SkillSummaryVO>> list(HttpServletRequest request) {
        String tenantId = tenantOf(request);
        List<ChatSkillState> states = skillStateService.listAll(tenantId);
        Map<String, ChatSkillState> stateByName = new java.util.HashMap<>();
        for (ChatSkillState s : states) {
            stateByName.put(s.getName(), s);
        }

        List<SkillSummaryVO> items = new ArrayList<>();
        for (String name : skillRegistry.listAll()) {
            SkillProvider provider = skillRegistry.getProvider(name);
            if (provider == null) continue;
            ChatSkillState st = stateByName.get(name);
            items.add(new SkillSummaryVO(
                    name,
                    provider.source().name(),
                    provider.version(),
                    st == null || Boolean.TRUE.equals(st.getEnabled()),
                    provider.builtin(),
                    st == null ? null : st.getLastLoadedAt(),
                    st == null ? null : st.getErrorMsg()
            ));
        }
        return ApiResponse.ok(items);
    }

    @GetMapping("/{name}")
    public ApiResponse<SkillDetailVO> detail(@PathVariable("name") String name,
                                             HttpServletRequest request) {
        String tenantId = tenantOf(request);
        SkillProvider provider = skillRegistry.getProvider(name);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "技能不存在: " + name);
        }
        ChatSkillState st = skillStateService.findOne(tenantId, name);
        SkillManifest manifest = skillRegistry.findManifest(name);
        Map<String, Object> schema = provider.schema();
        Map<String, Object> inputSchema = extractParameters(schema);
        String entry = manifest != null ? manifest.entry() : ("builtin:" + provider.getClass().getName());
        String manifestRaw = readManifestRaw(manifest);
        String sourcePath = manifest != null ? manifest.sourcePath() : null;

        SkillDetailVO vo = new SkillDetailVO(
                name,
                provider.source().name(),
                provider.version(),
                st == null || Boolean.TRUE.equals(st.getEnabled()),
                provider.builtin(),
                st == null ? null : st.getLastLoadedAt(),
                st == null ? null : st.getErrorMsg(),
                provider.description(),
                entry,
                inputSchema,
                manifestRaw,
                sourcePath
        );
        return ApiResponse.ok(vo);
    }

    @PostMapping("/{name}/toggle")
    public ApiResponse<Boolean> toggle(@PathVariable("name") String name,
                                       @Valid @RequestBody ToggleRequest body,
                                       HttpServletRequest request) {
        String tenantId = tenantOf(request);
        SkillProvider provider = skillRegistry.getProvider(name);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "技能不存在: " + name);
        }
        skillStateService.setEnabled(tenantId, name, body.enabled, operatorOf(request));
        chatAuditService.record(tenantId, request.getHeader(properties.getServer().getRequestHeader()),
                null, "skill.toggle",
                "name=" + name + ", enabled=" + body.enabled);
        log.info("[skill-admin] toggle name={}, enabled={}, tenant={}", name, body.enabled, tenantId);
        return ApiResponse.ok(body.enabled);
    }

    @PostMapping("/reload")
    public ApiResponse<List<String>> reload(HttpServletRequest request) {
        String tenantId = tenantOf(request);
        hotReloadScheduler.reloadAndRecord();
        chatAuditService.record(tenantId, request.getHeader(properties.getServer().getRequestHeader()),
                null, "skill.reload", "manual");
        return ApiResponse.ok(skillRegistry.listAll());
    }

    private String tenantOf(HttpServletRequest request) {
        String h = request.getHeader(properties.getServer().getTenantHeader());
        return (h == null || h.isBlank()) ? "default" : h;
    }

    private String operatorOf(HttpServletRequest request) {
        String op = request.getHeader("X-Operator");
        return (op == null || op.isBlank()) ? "system" : op;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParameters(Map<String, Object> schema) {
        if (schema == null) return Map.of();
        Object fn = schema.get("function");
        if (fn instanceof Map<?, ?> fnMap) {
            Object params = fnMap.get("parameters");
            if (params instanceof Map<?, ?> p) {
                return (Map<String, Object>) p;
            }
        }
        return Map.of();
    }

    private String readManifestRaw(SkillManifest manifest) {
        if (manifest == null || manifest.sourcePath() == null) return null;
        try {
            Path p = Paths.get(manifest.sourcePath());
            if (!Files.exists(p)) return null;
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[skill-admin] read manifest raw failed, path={}, err={}",
                    manifest.sourcePath(), e.getMessage());
            return null;
        }
    }

    public static class ToggleRequest {
        @NotNull
        public Boolean enabled;
    }

    @SuppressWarnings("unused")
    private SkillSource sourceOf(SkillProvider p) {
        return p == null ? SkillSource.BUILTIN : p.source();
    }
}
