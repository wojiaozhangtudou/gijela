package com.gijela.morpheus.pistil.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gijela.morpheus.pistil.support.sort.Sortable;

import java.io.Serializable;

/**
 * <p>
 * 岗位表
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
@TableName("aigc_sys_post")
public class SysPost implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Sortable
    private Long id;

    /**
     * 岗位名称
     */
    @TableField("name")
    @Sortable
    private String name;

    /**
     * 岗位编码
     */
    @TableField("code")
    @Sortable
    private String code;

    @TableField("sort")
    @Sortable
    private Integer sort;

    @TableField("status")
    private Byte status;

    @TableField("remark")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    @Override
    public String toString() {
        return "SysPost{" +
            "id = " + id +
            ", name = " + name +
            ", code = " + code +
            ", sort = " + sort +
            ", status = " + status +
            ", remark = " + remark +
            "}";
    }
}
