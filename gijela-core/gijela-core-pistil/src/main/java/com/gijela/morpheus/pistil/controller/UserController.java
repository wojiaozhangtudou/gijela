package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.domain.dto.*;
import com.gijela.morpheus.pistil.domain.vo.UserVO;
import com.gijela.morpheus.pistil.service.ISysUserService;
import com.gijela.morpheus.pistil.service.ISysUserDeptService;
import com.gijela.morpheus.pistil.service.ISysUserPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class UserController {

    @Autowired
    private ISysUserService userService;
    @Autowired
    private ISysUserDeptService userDeptService;
    @Autowired
    private ISysUserPostService userPostService;

    @PostMapping("/page")
    @Operation(summary = "用户分页查询")
    public ApiResponse<Page<UserVO>> page(@Valid @RequestBody UserPageDTO dto) {
        return ApiResponse.ok(userService.pageUsers(dto));
    }

    @PostMapping
    @Operation(summary = "新增或编辑用户")
    @PreAuthorize("#p0.id == null ? hasRoleOrAuthority('user:create') : hasRoleOrAuthority('user:update')")
    public ApiResponse<Long> save(@Valid @RequestBody SaveUserDTO dto) {
        return ApiResponse.ok(userService.saveUser(dto));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除用户")
    public ApiResponse<Void> delete(@Valid @RequestBody IdsDTO dto) {
        userService.deleteUsers(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/status")
    @Operation(summary = "修改用户状态")
    public ApiResponse<Void> changeStatus(@Valid @RequestBody ChangeStatusDTO dto) {
        userService.changeStatus(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/reset-pwd")
    @Operation(summary = "重置密码")
    public ApiResponse<Void> resetPwd(@Valid @RequestBody ResetPwdDTO dto) {
        userService.resetPassword(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/depts")
    @Operation(summary = "设置用户部门（替换）")
    public ApiResponse<Void> assignDepts(@Parameter(description = "用户ID") @PathVariable Long id,
                                         @Valid @RequestBody IdsDTO dto) {
        userDeptService.assignUserDepts(id, dto.getIds());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/depts")
    @Operation(summary = "查询用户部门ID列表")
    public ApiResponse<List<Long>> listDepts(@Parameter(description = "用户ID") @PathVariable Long id) {
        return ApiResponse.ok(userDeptService.listDeptIdsByUser(id));
    }

    @PostMapping("/{id}/posts")
    @Operation(summary = "设置用户岗位（替换）")
    public ApiResponse<Void> assignPosts(@Parameter(description = "用户ID") @PathVariable Long id,
                                         @Valid @RequestBody IdsDTO dto) {
        userPostService.assignUserPosts(id, dto.getIds());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/posts")
    @Operation(summary = "查询用户岗位ID列表")
    public ApiResponse<List<Long>> listPosts(@Parameter(description = "用户ID") @PathVariable Long id) {
        return ApiResponse.ok(userPostService.listPostIdsByUser(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public ApiResponse<UserVO> detail(@Parameter(description = "用户ID") @PathVariable Long id) {
        return ApiResponse.ok(userService.getUserDetail(id));
    }

    @PostMapping("/avatar")
    @Operation(summary = "更新头像")
    public ApiResponse<Void> updateAvatar(@Valid @RequestBody UpdateAvatarDTO dto) {
        userService.updateAvatar(dto);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/avatar")
    @Operation(summary = "获取用户头像Base64")
    public ApiResponse<String> getAvatar(@Parameter(description = "用户ID") @PathVariable Long id) {
        return ApiResponse.ok(userService.getAvatar(id));
    }
}
