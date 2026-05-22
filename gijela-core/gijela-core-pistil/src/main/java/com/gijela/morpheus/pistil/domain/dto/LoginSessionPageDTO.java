package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录会话分页查询入参")
public class LoginSessionPageDTO extends BasePageDTO {

    @Schema(description = "用户ID（为空时查询全部）")
    private Long userId;

    @Schema(description = "用户名（模糊匹配）")
    private String username;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
