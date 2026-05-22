package com.gijela.morpheus.pistil.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录会话信息")
public class LoginSessionVO {
    @Schema(description = "会话ID")
    private String sessionId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "登录时间戳(毫秒)")
    private Long loginAt;
    @Schema(description = "最近活跃时间戳(毫秒)")
    private Long lastActiveAt;
    @Schema(description = "客户端标识")
    private String clientLabel;
    @Schema(description = "设备编号")
    private String deviceNo;
    @Schema(description = "登录IP")
    private String loginIp;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getLoginAt() { return loginAt; }
    public void setLoginAt(Long loginAt) { this.loginAt = loginAt; }
    public Long getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Long lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public String getClientLabel() { return clientLabel; }
    public void setClientLabel(String clientLabel) { this.clientLabel = clientLabel; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getLoginIp() { return loginIp; }
    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }
}
