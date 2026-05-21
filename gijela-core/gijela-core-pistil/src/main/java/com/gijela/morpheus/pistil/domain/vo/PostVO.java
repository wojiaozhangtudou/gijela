package com.gijela.morpheus.pistil.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "岗位分页返回数据")
public class PostVO {
    @Schema(description = "主键ID")
    private Long id;
    @Schema(description = "岗位名称")
    private String name;
    @Schema(description = "岗位编码")
    private String code;
    @Schema(description = "排序")
    private Integer sort;
    @Schema(description = "状态 1启用 0禁用")
    private Byte status;
    @Schema(description = "备注")
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

