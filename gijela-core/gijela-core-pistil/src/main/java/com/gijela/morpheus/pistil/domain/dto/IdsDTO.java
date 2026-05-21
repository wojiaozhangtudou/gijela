package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "批量ID通用入参")
public class IdsDTO {
    @Schema(description = "ID列表，1..100")
    @NotEmpty(message = "ids 不能为空")
    @Size(min = 1, max = 100, message = "ids 长度需在 1..100")
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}

