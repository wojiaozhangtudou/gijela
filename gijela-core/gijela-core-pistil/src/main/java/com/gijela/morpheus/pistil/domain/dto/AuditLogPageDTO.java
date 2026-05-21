package com.gijela.morpheus.pistil.domain.dto;

import com.gijela.morpheus.pistil.support.query.QueryField;
import com.gijela.morpheus.pistil.support.query.QueryOperator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "审计日志分页查询入参")
public class AuditLogPageDTO extends BaseSortPageDTO {
    @Schema(description = "用户名模糊")
    @QueryField(op = QueryOperator.LIKE, column = "username")
    private String username;
    @Schema(description = "操作关键字模糊")
    @QueryField(op = QueryOperator.LIKE, column = "operation")
    private String operation;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
}

