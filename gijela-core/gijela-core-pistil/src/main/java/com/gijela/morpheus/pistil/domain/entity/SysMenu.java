package com.gijela.morpheus.pistil.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gijela.morpheus.pistil.support.sort.Sortable;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 菜单/权限表
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
@TableName("aigc_sys_menu")
public class SysMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Sortable // 允许按 id 排序
    private Long id;

    /**
     * 父ID
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 名称
     */
    @TableField("name")
    @Sortable // 允许按名称排序
    private String name;

    /**
     * D目录 M菜单 B按钮
     */
    @TableField("type")
    private String type;

    /**
     * 路由/URI
     */
    @TableField("path")
    private String path;

    /**
     * 权限标识
     */
    @TableField("permission")
    private String permission;

    @TableField("icon")
    private String icon;

    @TableField("sort")
    @Sortable // 允许按 sort 字段排序
    private Integer sort;

    @TableField("status")
    private Byte status;

    @TableField("remark")
    private String remark;

    @TableField("create_time")
    @Sortable // 允许按创建时间排序（驼峰自动转 create_time）
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "SysMenu{" +
            "id = " + id +
            ", parentId = " + parentId +
            ", name = " + name +
            ", type = " + type +
            ", path = " + path +
            ", permission = " + permission +
            ", icon = " + icon +
            ", sort = " + sort +
            ", status = " + status +
            ", remark = " + remark +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
