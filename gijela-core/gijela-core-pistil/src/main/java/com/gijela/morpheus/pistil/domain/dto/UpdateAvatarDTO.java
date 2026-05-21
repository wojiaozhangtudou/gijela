package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "更新用户头像入参")
public class UpdateAvatarDTO {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "userId 不能为空")
    private Long userId;

    @Schema(description = "头像Base64(不含data前缀，<=60000字符)")
    @Size(max = 60000, message = "avatarBase64 超过限制")
    private String avatarBase64;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAvatarBase64() { return avatarBase64; }
    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }
}
