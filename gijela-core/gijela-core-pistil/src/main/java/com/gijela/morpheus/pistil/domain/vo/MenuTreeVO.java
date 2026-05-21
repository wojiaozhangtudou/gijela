package com.gijela.morpheus.pistil.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "菜单树节点")
public class MenuTreeVO {
    @Schema(description = "ID")
    private Long id;
    @Schema(description = "父ID")
    private Long parentId;
    @Schema(description = "名称")
    private String name;
    @Schema(description = "类型 D/M/B")
    private String type;
    @Schema(description = "路由或URL")
    private String path;
    @Schema(description = "权限标识")
    private String permission;
    @Schema(description = "图标")
    private String icon;
    @Schema(description = "排序")
    private Integer sort;
    @Schema(description = "状态 1启用 0禁用")
    private Byte status;
    @Schema(description = "子节点")
    private List<MenuTreeVO> children = new ArrayList<>();

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
    public List<MenuTreeVO> getChildren() { return children; }
    public void setChildren(List<MenuTreeVO> children) { this.children = children; }
}

