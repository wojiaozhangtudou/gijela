package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.domain.dto.AuditLogPageDTO;
import com.gijela.morpheus.pistil.domain.vo.AuditLogVO;
import com.gijela.morpheus.pistil.service.ISysAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "审计日志")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class AuditLogController {

    @Autowired
    private ISysAuditLogService auditLogService;

    @PostMapping("/page")
    @Operation(summary = "审计日志分页查询")
    public ApiResponse<Page<AuditLogVO>> page(@Valid @RequestBody AuditLogPageDTO dto) {
        return ApiResponse.ok(auditLogService.pageLogs(dto));
    }
}

