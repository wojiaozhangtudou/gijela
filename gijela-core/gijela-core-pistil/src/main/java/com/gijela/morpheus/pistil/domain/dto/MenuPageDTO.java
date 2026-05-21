package com.gijela.morpheus.pistil.domain.dto;

import com.gijela.morpheus.pistil.support.query.QueryField;
import com.gijela.morpheus.pistil.support.query.QueryOperator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MenuPageDTO", description = "菜单分页查询入参（继承通用分页+排序基类，排序字段动态基于 @Sortable 反射校验）")
public class MenuPageDTO extends BaseSortPageDTO {

    @Schema(description = "按名称模糊查询", example = "系统")
    @QueryField(op = QueryOperator.LIKE, column = "name")
    private String name;

    @Schema(description = "类型：D目录 M菜单 B按钮", example = "M")
    @QueryField(op = QueryOperator.EQ, column = "type")
    private String type;

    @Schema(description = "状态：1启用 0禁用", example = "1")
    @QueryField(op = QueryOperator.EQ, column = "status")
    private Byte status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
}
