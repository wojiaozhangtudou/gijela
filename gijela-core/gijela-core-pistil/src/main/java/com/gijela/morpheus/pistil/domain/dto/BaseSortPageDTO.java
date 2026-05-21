package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 通用“分页 + 单字段排序”基类。
 * 子类可通过在 sortField 上追加 @Pattern(regexp=...) 约束白名单。
 */
@Schema(description = "通用分页 + 排序参数基类")
public class BaseSortPageDTO extends BasePageDTO {

    @Schema(description = "排序字段（子类可加 @Pattern 进行白名单约束）", example = "createTime")
    private String sortField;

    @Schema(description = "排序方向：ASC 或 DESC，默认 DESC", example = "DESC")
    @Pattern(regexp = "(?i)ASC|DESC", message = "sortOrder 只能为 ASC 或 DESC")
    private String sortOrder = "DESC";

    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
}

