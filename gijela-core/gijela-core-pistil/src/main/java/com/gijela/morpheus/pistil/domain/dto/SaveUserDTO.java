package com.gijela.morpheus.pistil.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "新增/编辑用户入参")
public class SaveUserDTO {
    @Schema(description = "用户ID，编辑必填")
    private Long id;
    @Schema(description = "登录账号", example = "admin")
    @NotBlank(message = "username 不能为空")
    private String username;
    @Schema(description = "密码（新增必填，编辑可为空表示不修改）", example = "P@ssw0rd")
    private String password;
    @Schema(description = "姓名", example = "张三")
    @NotBlank(message = "nickname 不能为空")
    private String nickname;
    @Schema(description = "邮箱")
    @Email(message = "email 格式非法")
    private String email;
    @Schema(description = "手机号")
    @Size(max = 20, message = "phone 长度不能超过20")
    private String phone;
    @Schema(description = "状态 1启用 0禁用", example = "1")
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 非法")
    @Max(value = 1, message = "status 非法")
    private Byte status;
    @Schema(description = "部门ID")
    private Long deptId;
    @Schema(description = "岗位ID")
    private Long postId;
    @Schema(description = "部门ID列表（多部门关联）")
    private List<Long> deptIds;
    @Schema(description = "岗位ID列表（多岗位关联）")
    private List<Long> postIds;
    @Schema(description = "关联角色ID列表")
    private List<Long> roleIds;
    @Schema(description = "头像Base64(不含data前缀，<=60000字符)")
    @Size(max = 60000, message = "avatarBase64 超过限制")
    private String avatarBase64;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public List<Long> getDeptIds() { return deptIds; }
    public void setDeptIds(List<Long> deptIds) { this.deptIds = deptIds; }
    public List<Long> getPostIds() { return postIds; }
    public void setPostIds(List<Long> postIds) { this.postIds = postIds; }
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
    public String getAvatarBase64() { return avatarBase64; }
    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }
}
