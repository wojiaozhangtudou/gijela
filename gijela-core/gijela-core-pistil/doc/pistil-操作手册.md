# pistil 管理后台操作手册

> 发布版文档，适用于业务管理员、系统管理员、测试同学。

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0.0 |
| 更新时间 | 2026-05-22 |
| 适用范围 | `gijela-bloom/gijela-bloom-pistil` + `gijela-core/gijela-core-pistil` |
| 运行环境 | 本地联调环境 `http://localhost:5173/` |

## 目录

- [1. 系统访问](#system-access)
- [2. 登录与退出](#login-logout)
- [3. 页面导航总览](#navigation-overview)
- [4. 用户管理](#user-management)
- [5. 角色与菜单管理](#role-menu-management)
  - [5.1 角色管理](#role-management)
  - [5.2 菜单管理](#menu-management)
- [6. 部门与岗位管理](#org-management)
  - [6.1 部门管理](#dept-management)
  - [6.2 岗位管理](#post-management)
- [7. 审计日志](#audit-log)
- [8. 会话管理](#session-management)
- [9. 个人中心](#profile-center)
- [10. 权限建议](#permission-advice)
- [11. 日常巡检建议](#inspection-advice)
- [12. 常见问题](#faq)
- [13. 项目地址](#project-links)

---

<a id="system-access"></a>

## 1. 系统访问

- 前端地址：`http://localhost:5173/`
- 后端接口前缀：`/api/v1/**`
- 登录入口：`/login`

> 本手册截图均来自本地联调环境，仅用于功能说明与操作指引。

<p align="center">
  <img src="./assets/pistil/01-login.png" alt="登录页" width="920" />
</p>
<p align="center"><em>图 1-1 登录页</em></p>

---

<a id="login-logout"></a>

## 2. 登录与退出

### 2.1 登录

1. 打开登录页，输入用户名和密码。
2. 点击“登录”。
3. 登录成功后进入 Dashboard 首页。

本地初始化账号（如使用默认初始化数据）：
- 用户名：`admin`
- 密码：`123456`

登录机制说明：
- Access Token：短期使用，保存在前端内存态。
- Refresh Token：保存在 HttpOnly Cookie。
- 支持自动刷新与会话治理。

### 2.2 退出

1. 点击右上角用户菜单。
2. 选择“退出”。
3. 系统调用后端注销接口，清理会话与 Cookie。

---

<a id="navigation-overview"></a>

## 3. 页面导航总览

常用菜单如下：

1. Dashboard（首页）
2. 用户管理（`/system/users`）
3. 角色管理（`/sys/roles`）
4. 菜单管理（`/sys/menus`）
5. 部门管理（`/org/dept`）
6. 岗位管理（`/org/post`）
7. 审计日志（`/sys/audit`）
8. 会话管理（`/system/sessions`）
9. 个人中心（`/profile`）

<p align="center">
  <img src="./assets/pistil/02-dashboard.png" alt="Dashboard" width="920" />
</p>
<p align="center"><em>图 3-1 Dashboard 首页</em></p>

---

<a id="user-management"></a>

## 4. 用户管理

功能说明：
- 查询用户
- 新增用户
- 编辑用户
- 状态管理
- 关联角色/组织信息（视权限而定）

操作路径：
- 菜单：用户管理
- 页面：`/system/users`

标准操作：
1. 在列表输入筛选条件并查询。
2. 点击“新增用户”填写表单并保存。
3. 点击“编辑”修改用户信息。
4. 点击“删除”可删除指定用户。

<p align="center">
  <img src="./assets/pistil/03-users.png" alt="用户管理列表" width="920" />
</p>
<p align="center"><em>图 4-1 用户管理列表</em></p>

<p align="center">
  <img src="./assets/pistil/13-users-create-dialog.png" alt="新增用户" width="920" />
</p>
<p align="center"><em>图 4-2 新增用户弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/21-users-edit-dialog.png" alt="编辑用户" width="920" />
</p>
<p align="center"><em>图 4-3 编辑用户弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/28-users-delete-confirm.png" alt="删除用户确认" width="920" />
</p>
<p align="center"><em>图 4-4 删除用户确认弹窗</em></p>

---

<a id="role-menu-management"></a>

## 5. 角色与菜单管理

<a id="role-management"></a>

### 5.1 角色管理

操作路径：`/sys/roles`

可执行动作：
- 新建角色
- 编辑角色
- 删除角色
- 维护角色状态

<p align="center">
  <img src="./assets/pistil/07-roles.png" alt="角色管理列表" width="920" />
</p>
<p align="center"><em>图 5-1 角色管理列表</em></p>

<p align="center">
  <img src="./assets/pistil/14-roles-create-dialog.png" alt="新增角色" width="920" />
</p>
<p align="center"><em>图 5-2 新增角色弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/22-roles-edit-dialog.png" alt="编辑角色" width="920" />
</p>
<p align="center"><em>图 5-3 编辑角色弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/29-roles-delete-confirm.png" alt="删除角色确认" width="920" />
</p>
<p align="center"><em>图 5-4 删除角色确认弹窗</em></p>

<a id="menu-management"></a>

### 5.2 菜单管理

操作路径：`/sys/menus`

可执行动作：
- 新建目录、菜单、按钮
- 维护路由路径与权限标识
- 调整层级结构
- 编辑或删除既有菜单项

<p align="center">
  <img src="./assets/pistil/08-menus.png" alt="菜单管理列表" width="920" />
</p>
<p align="center"><em>图 5-5 菜单管理列表</em></p>

<p align="center">
  <img src="./assets/pistil/15-menus-create-dialog.png" alt="新增菜单" width="920" />
</p>
<p align="center"><em>图 5-6 新增菜单弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/24-menus-edit-dialog.png" alt="编辑菜单" width="920" />
</p>
<p align="center"><em>图 5-7 编辑菜单弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/30-menus-delete-confirm.png" alt="删除菜单确认" width="920" />
</p>
<p align="center"><em>图 5-8 删除菜单确认弹窗</em></p>

---

<a id="org-management"></a>

## 6. 部门与岗位管理

<a id="dept-management"></a>

### 6.1 部门管理

- 路径：`/org/dept`
- 功能：维护部门树、负责人、联系方式等。

<p align="center">
  <img src="./assets/pistil/09-dept.png" alt="部门管理列表" width="920" />
</p>
<p align="center"><em>图 6-1 部门管理列表</em></p>

<p align="center">
  <img src="./assets/pistil/16-dept-create-dialog.png" alt="新增部门" width="920" />
</p>
<p align="center"><em>图 6-2 新增部门弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/25-dept-edit-dialog.png" alt="编辑部门" width="920" />
</p>
<p align="center"><em>图 6-3 编辑部门弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/31-dept-delete-confirm.png" alt="删除部门确认" width="920" />
</p>
<p align="center"><em>图 6-4 删除部门确认弹窗</em></p>

<a id="post-management"></a>

### 6.2 岗位管理

- 路径：`/org/post`
- 功能：维护岗位编码、名称、排序、状态。

<p align="center">
  <img src="./assets/pistil/10-post.png" alt="岗位管理列表" width="920" />
</p>
<p align="center"><em>图 6-5 岗位管理列表</em></p>

<p align="center">
  <img src="./assets/pistil/17-post-create-dialog.png" alt="新增岗位" width="920" />
</p>
<p align="center"><em>图 6-6 新增岗位弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/26-post-edit-dialog.png" alt="编辑岗位" width="920" />
</p>
<p align="center"><em>图 6-7 编辑岗位弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/32-post-delete-confirm.png" alt="删除岗位确认" width="920" />
</p>
<p align="center"><em>图 6-8 删除岗位确认弹窗</em></p>

---

<a id="audit-log"></a>

## 7. 审计日志

路径：`/sys/audit`

用途：
- 查询操作记录
- 定位异常行为
- 回溯关键变更

建议：
- 结合会话管理中的 `deviceNo`、`loginIp` 一起排查账号风险。

<p align="center">
  <img src="./assets/pistil/05-audit.png" alt="审计日志列表" width="920" />
</p>
<p align="center"><em>图 7-1 审计日志列表</em></p>

<p align="center">
  <img src="./assets/pistil/20-audit-filter.png" alt="审计日志条件筛选" width="920" />
</p>
<p align="center"><em>图 7-2 审计日志条件筛选</em></p>

---

<a id="session-management"></a>

## 8. 会话管理（重点）

路径：`/system/sessions`

功能：
1. 会话分页查询。
2. 按用户 ID、用户名筛选。
3. 单会话踢出。
4. 用户全部会话踢出。
5. 查看会话字段：`sessionId`、`deviceNo`、`loginIp`、`clientLabel`、`loginAt`、`lastActiveAt`。

常见场景：
- 发现异常设备登录，执行单会话踢出。
- 账号疑似泄露，执行全部会话踢出。

<p align="center">
  <img src="./assets/pistil/04-sessions.png" alt="会话管理列表" width="920" />
</p>
<p align="center"><em>图 8-1 会话管理列表</em></p>

<p align="center">
  <img src="./assets/pistil/19-sessions-filter.png" alt="会话管理条件筛选" width="920" />
</p>
<p align="center"><em>图 8-2 会话管理条件筛选</em></p>

<p align="center">
  <img src="./assets/pistil/27-sessions-kickout-confirm.png" alt="会话踢出确认" width="920" />
</p>
<p align="center"><em>图 8-3 会话踢出确认弹窗</em></p>

---

<a id="profile-center"></a>

## 9. 个人中心

路径：`/profile`

功能：
1. 查看和编辑个人基础信息。
2. 修改密码。
3. 通过弹窗管理登录设备，不跳转新页。

“登录设备管理”可执行：
- 刷新会话列表
- 单设备下线
- 全部设备下线

<p align="center">
  <img src="./assets/pistil/06-profile.png" alt="个人中心" width="920" />
</p>
<p align="center"><em>图 9-1 个人中心</em></p>

<p align="center">
  <img src="./assets/pistil/18-profile-change-password-dialog.png" alt="修改密码" width="920" />
</p>
<p align="center"><em>图 9-2 修改密码弹窗</em></p>

<p align="center">
  <img src="./assets/pistil/12-profile-device-dialog.png" alt="登录设备管理" width="920" />
</p>
<p align="center"><em>图 9-3 登录设备管理弹窗</em></p>

---

<a id="permission-advice"></a>

## 10. 权限建议

建议最小权限划分：

- 普通管理员：用户、角色、菜单、组织维护权限。
- 安全管理员：额外拥有会话管理权限。
  - `auth:session:list`
  - `auth:session:kickout`
  - `auth:session:kickoutAll`

---

<a id="inspection-advice"></a>

## 11. 日常巡检建议

每日建议检查：
1. 审计日志是否存在异常登录操作。
2. 会话列表是否有可疑 `loginIp` 或 `deviceNo`。
3. 是否存在长期不活跃但未下线的会话。

每周建议检查：
1. 高权限账号会话数是否异常。
2. 菜单权限是否存在误配。
3. 角色变更是否留有审计记录。

---

<a id="faq"></a>

## 12. 常见问题

### Q1：被踢出后为什么还可继续操作？

- 检查 `security.jwt.enableAccessSessionCheck` 是否开启。

### Q2：退出后为何登录仍提示在线数已满？

- 确认前端已调用后端 `/auth/logout`，而不是只清理本地 token。

### Q3：`deviceNo` 是真实硬件号吗？

- 不是。当前是前端生成并持久化的客户端设备标识。

---

<a id="project-links"></a>

## 13. 项目地址

- GitHub：https://github.com/wojiaozhangtudou/gijela
- Gitee：https://gitee.com/zhangjq123/gijela

项目目录：
- 后端：`gijela-core/gijela-core-pistil`
- 前端：`gijela-bloom/gijela-bloom-pistil`

---

