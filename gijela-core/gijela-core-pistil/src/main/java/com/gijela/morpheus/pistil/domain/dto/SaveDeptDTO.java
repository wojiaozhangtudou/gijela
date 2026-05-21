package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "新增/编辑部门入参")
public class SaveDeptDTO {
    @Schema(description = "部门ID，编辑时必填")
    private Long id;
    @Schema(description = "父部门ID，根节点传0或不传", example = "0")
    @Min(value = 0, message = "parentId 不能小于0")
    private Long parentId = 0L;
    @Schema(description = "部门名称", example = "技术部")
    @NotBlank(message = "name 不能为空")
    private String name;
    @Schema(description = "负责人")
    private String leader;
    @Schema(description = "联系电话")
    private String phone;
    @Schema(description = "邮箱")
    @Email(message = "email 格式非法")
    private String email;
    @Schema(description = "排序", example = "10")
    @Min(value = 0, message = "sort 不能小于0")
    private Integer sort = 0;
    @Schema(description = "状态 1启用 0禁用")
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 非法")
    @Max(value = 1, message = "status 非法")
    private Byte status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLeader() { return leader; }
    public void setLeader(String leader) { this.leader = leader; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
}

