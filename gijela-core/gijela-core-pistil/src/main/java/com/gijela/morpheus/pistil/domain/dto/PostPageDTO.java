package com.gijela.morpheus.pistil.domain.dto;

import com.gijela.morpheus.pistil.support.query.QueryField;
import com.gijela.morpheus.pistil.support.query.QueryOperator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "岗位分页查询入参")
public class PostPageDTO extends BaseSortPageDTO {
    @Schema(description = "岗位名称模糊")
    @QueryField(op = QueryOperator.LIKE, column = "name")
    private String name;
    @Schema(description = "岗位编码模糊")
    @QueryField(op = QueryOperator.LIKE, column = "code")
    private String code;
    @Schema(description = "状态 1启用 0禁用")
    @QueryField(op = QueryOperator.EQ, column = "status")
    private Byte status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
}

