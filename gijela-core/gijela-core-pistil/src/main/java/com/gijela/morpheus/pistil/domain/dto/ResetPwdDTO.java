package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "重置密码入参")
public class ResetPwdDTO {
    @Schema(description = "用户ID")
    @NotNull(message = "id 不能为空")
    private Long id;
    @Schema(description = "新密码")
    @NotBlank(message = "password 不能为空")
    private String password;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

