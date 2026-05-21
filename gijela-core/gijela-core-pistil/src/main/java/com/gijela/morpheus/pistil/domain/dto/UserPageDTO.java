package com.gijela.morpheus.pistil.domain.dto;

import com.gijela.morpheus.pistil.support.query.QueryField;
import com.gijela.morpheus.pistil.support.query.QueryOperator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户分页查询入参")
public class UserPageDTO extends BaseSortPageDTO {
    @Schema(description = "用户名模糊")
    @QueryField(op = QueryOperator.LIKE, column = "username")
    private String username;
    @Schema(description = "状态 1启用 0禁用")
    @QueryField(op = QueryOperator.EQ, column = "status")
    private Byte status;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
}

