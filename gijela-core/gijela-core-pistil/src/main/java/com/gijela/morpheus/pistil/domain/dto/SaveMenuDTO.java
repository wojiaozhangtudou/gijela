package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "新增或编辑菜单入参")
public class SaveMenuDTO {
    @Schema(description = "主键ID，编辑必填")
    private Long id;

    @Schema(description = "父菜单ID，根节点填 0", example = "0")
    @Min(value = 0, message = "parentId 不能小于 0")
    private Long parentId = 0L;

    @Schema(description = "名称", example = "系统管理")
    @NotBlank(message = "name 不能为空")
    @Size(max = 50, message = "name 长度不能超过 50")
    private String name;

    @Schema(description = "类型：D目录 M菜单 B按钮 S特殊", example = "M", allowableValues = {"D","M","B","S"})
    @Pattern(regexp = "[DMBS]", message = "type 只能为 D/M/B/S")
    private String type;

    @Schema(description = "路由或URL", example = "/system/user")
    @Size(max = 255, message = "path 长度不能超过 255")
    private String path;

    @Schema(description = "权限标识", example = "user:account:add")
    @Size(max = 100, message = "permission 长度不能超过 100")
    private String permission;

    @Schema(description = "图标", example = "icon-user")
    @Size(max = 50, message = "icon 长度不能超过 50")
    private String icon;

    @Schema(description = "排序，越小越前", example = "10")
    @Min(value = 0, message = "sort 不能小于 0")
    private Integer sort = 0;

    @Schema(description = "状态：1启用 0禁用", example = "1")
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 非法")
    @Max(value = 1, message = "status 非法")
    private Byte status;

    @Schema(description = "备注", example = "系统基础菜单")
    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
