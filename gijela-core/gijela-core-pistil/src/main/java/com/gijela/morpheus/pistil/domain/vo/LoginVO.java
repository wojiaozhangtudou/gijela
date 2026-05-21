package com.gijela.morpheus.pistil.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录响应数据")
public class LoginVO {
    @Schema(description = "访问令牌")
    private String accessToken;
    @Schema(description = "刷新令牌")
    private String refreshToken;
    @Schema(description = "访问令牌过期时间戳(毫秒)")
    private long accessExpireAt;
    @Schema(description = "刷新令牌过期时间戳(毫秒)")
    private long refreshExpireAt;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "昵称")
    private String nickname;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getAccessExpireAt() { return accessExpireAt; }
    public void setAccessExpireAt(long accessExpireAt) { this.accessExpireAt = accessExpireAt; }
    public long getRefreshExpireAt() { return refreshExpireAt; }
    public void setRefreshExpireAt(long refreshExpireAt) { this.refreshExpireAt = refreshExpireAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
