package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "修改状态入参")
public class ChangeStatusDTO {
    @Schema(description = "主键ID", example = "1")
    @NotNull(message = "id 不能为空")
    private Long id;
    @Schema(description = "状态：1启用 0禁用", example = "1")
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 非法")
    @Max(value = 1, message = "status 非法")
    private Byte status;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
}

