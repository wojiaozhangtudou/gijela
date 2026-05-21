package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "角色分配权限(菜单)入参")
public class RolePermsDTO {
    @Schema(description = "角色ID")
    @NotNull(message = "roleId 不能为空")
    private Long roleId;
    @Schema(description = "菜单ID集合，可为空表示清空")
    @Size(max = 500, message = "菜单ID数量不能超过500")
    private List<Long> menuIds;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public List<Long> getMenuIds() { return menuIds; }
    public void setMenuIds(List<Long> menuIds) { this.menuIds = menuIds; }
}

