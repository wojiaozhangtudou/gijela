package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通用分页基础 DTO，仅包含分页页码与大小；排序与过滤字段由子类自行扩展。
 * 适用规则：current 从 1 开始；size 1..200。
 */
@Schema(description = "通用分页参数基类")
public class BasePageDTO {

    @Schema(description = "当前页，从 1 开始", example = "1")
    @Min(value = 1, message = "current 不能小于 1")
    private int current = 1;

    @Schema(description = "每页大小，1..200", example = "20")
    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 200, message = "size 不能大于 200")
    private int size = 20;

    public int getCurrent() { return current; }
    public void setCurrent(int current) { this.current = current; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}

