I18N 术语表（中 — 英）

目的

为前端界面常用术语提供统一的英文翻译与建议 key 命名，减少翻译歧义，保证多语言体验一致性。

使用说明

- 首选英文字面翻译作为 key 命名的一部分，例如：`message.login`、`user.email`。
- key 命名遵循点号分层（namespace），示例：`page.users.title`、`validation.required`。
- 若上下文可能造成歧义，请在 PR 描述中注明上下文或在 locales JSON 添加注释字段 `_comment`。

常用术语

- 登录 — Login — key 示例：`message.login`、`page.login.title`
- 用户 — User — `user.*`（`user.id`, `user.name`, `user.email`）
- 角色 — Role — `role.*`（`role.name`, `role.description`）
- 权限 — Permission — `permission.*`
- 菜单 — Menu — `menu.*`（`menu.name`, `menu.path`）
- 部门 — Department — `dept.*` 或 `department.*`（`dept.name`）
- 岗位 — Post / Position — `post.*`（`post.name`）
- 审计日志 — Audit Log — `audit.*`（`audit.log`, `audit.time`）
- 创建 — Create — `action.create` 或 `user.create`
- 编辑 — Edit — `action.edit` 或 `user.edit`
- 删除 — Delete — `action.delete` 或 `user.delete`
- 查询 / 搜索 — Search / Query — `action.search` / `page.users.search_placeholder`
- 确认 — Confirm — `message.confirm`
- 取消 — Cancel — `message.cancel`
- 保存 — Save — `message.save`
- 详情 — Details — `message.details` 或 `page.users.details`
- 状态 — Status — `message.status`（状态值建议统一：`status.active` / `status.inactive`）
- 启用 / 禁用 — Enable / Disable — `action.enable` / `action.disable`
- 成功 — Success — `message.success`
- 失败 — Failed / Error — `message.failed` / `message.error`
- 提示 — Tip / Hint — `message.tip`
- 警告 — Warning — `message.warning`
- 验证 / 校验 — Validation — `validation.*`（`validation.required`, `validation.email`）

变量与格式化示例

- 占位变量命名：使用短小英文（小写）：`{name}`, `{count}`, `{date}`。
- 示例：`message.welcome = "Welcome, {name}!"`（中文：`message.welcome = "欢迎，{name}！"`）

翻译风格建议

- 术语表中的英文为首选翻译，若需要替换或本地化，请在 PR 中说明理由。
- 专有名词（产品名、公司名）保持原文不翻译。

维护与扩展

- 新增术语请在此文件追加，并在 PR 描述中列出变更。
- 建议定期（每次 release）审查术语表并同步到翻译团队。

示例 i18n key 快速映射

- 登录页标题：`page.login.title` -> "登录" / "Login"
- 用户列表标题：`page.users.title` -> "用户管理" / "User Management"
- 新建用户按钮：`action.create_user` -> "创建用户" / "Create User"
- 删除确认：`message.confirm_delete` -> "确定删除吗？" / "Are you sure to delete?"

END
