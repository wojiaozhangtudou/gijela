package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.domain.dto.*;
import com.gijela.morpheus.pistil.domain.vo.RoleVO;
import com.gijela.morpheus.pistil.service.ISysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "角色管理")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class RoleController {

    @Autowired
    private ISysRoleService roleService;

    @PostMapping("/page")
    @Operation(summary = "角色分页查询")
    public ApiResponse<Page<RoleVO>> page(@Valid @RequestBody RolePageDTO dto) {
        return ApiResponse.ok(roleService.pageRoles(dto));
    }

    @PostMapping
    @Operation(summary = "新增或编辑角色")
    public ApiResponse<Long> save(@Valid @RequestBody SaveRoleDTO dto) {
        return ApiResponse.ok(roleService.saveRole(dto));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除角色")
    public ApiResponse<Void> delete(@Valid @RequestBody IdsDTO dto) {
        roleService.deleteRoles(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/perms")
    @Operation(summary = "分配角色菜单权限")
    public ApiResponse<Void> assignPerms(@Valid @RequestBody RolePermsDTO dto) {
        roleService.assignPerms(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/status")
    @Operation(summary = "修改角色状态")
    public ApiResponse<Void> changeStatus(@Valid @RequestBody ChangeStatusDTO dto) {
        roleService.changeStatus(dto);
        return ApiResponse.ok(null);
    }

    @GetMapping("/menu-ids")
    @Operation(summary = "查询角色已授权菜单ID")
    public ApiResponse<List<Long>> roleMenuIds(@Parameter(description = "角色ID") @RequestParam Long roleId) {
        return ApiResponse.ok(roleService.getRoleMenuIds(roleId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "角色详情")
    public ApiResponse<RoleVO> detail(@Parameter(description = "角色ID") @PathVariable Long id) {
        return ApiResponse.ok(roleService.getRoleDetail(id));
    }
}
