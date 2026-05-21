## 快速开始

1) 安装依赖（推荐 pnpm）：

```powershell
pnpm install
pnpm dev
```

2) 配置后端地址：在项目根目录创建 `.env` 或设置环境变量 `VITE_API_BASE` 指向后端，例如 `http://127.0.0.1:9006`。

3) 功能与扩展：当前实现为最小骨架，包含登录、仪表盘、个人资料、以及从 `/api/v1/menus/tree` 加载菜单并注册动态路由。后续可以为每个菜单项添加单独的页面组件、权限校验和 CRUD 页面。

## 说明
- 交互与 API 客户端位于 `src/api`。
- 路由与动态路由加载位于 `src/router`。
- 状态管理（token/login）位于 `src/store`。

## README

简要说明：基于 Vue 3 + Element Plus + pnpm，参考 vue-element-admin 构建一套企业级管理后台，包含登录、首页、个人资料修改、以及菜单中列出的所有 CRUD 功能（用户、角色、菜单、部门、岗位、审计日志等），实现基于后端返回菜单树的动态路由与 RBAC 权限控制。

## 一、目标（Scope）
- 提供完整的管理平台骨架：登录页、仪表盘主页、侧栏菜单、顶部栏（包含头像和个人资料修改）、以及菜单中所有功能页面。
- 权限：基于后端权限标识（permission）实现页面/按钮级控制。
- 后端接口风格： 参考 `api-conventions.md`
- 与后端契合：使用 `api-docs.yaml` 自动生成或手写类型化 API 客户端，兼容后端 ApiResponse 格式。RAG 能力由独立前端模块承载，不在当前 `gijela-bloom-pistil` 内复用。

## 二、关键信息
- 后端基础接口可用，Swagger 地址：`http://127.0.0.1:9006/doc.html`。
- 所有受保护接口使用 Bearer JWT；登录接口 `POST /api/v1/auth/login` 返回 token（或能通过额外接口获取用户信息与权限）。
- 菜单树接口为 `GET /api/v1/menus/tree`，其返回在 `ApiResponse.data` 中为 MenuNode[]。

## 三、技术栈
- 框架：Vue 3 + TypeScript
- UI：Element Plus
- 包管理：pnpm
- 路由：vue-router 4
- 状态管理：Pinia
- HTTP：axios（或由 openapi 生成的 fetch/axios 客户端）
- 开发工具：Vite、ESLint、Prettier、Vitest（可选）
- API 生成工具（可选）：swagger-typescript-api / openapi-typescript

## 四、UI 风格与布局
- 参考 vue-element-admin 构建，地址 https://panjiachen.github.io/vue-element-admin-site/zh/guide/

## i18n 注意事项

当你在界面代码中新增文本键（例如新增校验消息 `validation.password_min_6`）时，请务必同时在 `src/i18n/locales/en.json` 和 `src/i18n/locales/zh.json` 中添加对应的翻译条目。项目中的文案尽量统一使用 i18n 键，这样才能保证多语言环境下不会出现缺失文本。一个简单的工作流程：

- 在代码中引入新的 i18n 键。
- 立即在 `src/i18n/locales/en.json` 和 `src/i18n/locales/zh.json` 中添加占位翻译（英文/中文）。
- 如果项目启用了类型检查或 JSON 校验，确保 JSON 格式正确（注意逗号和引号）。
- 需要时同步更新 `I18N_GUIDELINES.md` 中的术语或标准翻译。

示例：刚才新增的校验键示例条目：

- `src/i18n/locales/en.json` 添加：
	- `"enter_password": "Please enter password"`
	- `"password_min_6": "Password must be at least 6 characters"`
- `src/i18n/locales/zh.json` 添加：
	- `"enter_password": "请输入密码"`
	- `"password_min_6": "密码至少 6 位数字"`

保持翻译文件的同步可以减少运行或构建时的缺失翻译问题，也方便后续由翻译团队统一替换占位文案。