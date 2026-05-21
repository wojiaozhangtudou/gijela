package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "新增/编辑岗位入参")
public class SavePostDTO {
    @Schema(description = "岗位ID，编辑必填")
    private Long id;
    @Schema(description = "岗位名称", example = "开发工程师")
    @NotBlank(message = "name 不能为空")
    private String name;
    @Schema(description = "岗位编码", example = "DEV_ENGINEER")
    @NotBlank(message = "code 不能为空")
    private String code;
    @Schema(description = "排序", example = "10")
    @Min(value = 0, message = "sort 不能小于0")
    private Integer sort = 0;
    @Schema(description = "状态 1启用 0禁用")
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 非法")
    @Max(value = 1, message = "status 非法")
    private Byte status;
    @Schema(description = "备注")
    @Size(max = 255, message = "remark 最长255")
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

