---
applyTo: "gijela-bloom/gijela-bloom-pistil/**/*"
---

# pistil 前端模块定制说明

- 当前匹配范围为 `gijela-bloom/gijela-bloom-pistil/`。
- 修改前优先理解现有结构：`src/api`、`src/store`、`src/views`、`src/components`、`src/router`。
- 新接口优先补在 `src/api/*.ts`，并通过页面/Store 使用，不在页面中散写 axios 细节。
- 菜单与权限相关改动优先复用动态路由与按钮权限现有模式，保持与后端权限标识一致。
- 用户、角色、菜单、部门、岗位、审计日志等系统页面尽量复用既有表单、列表和弹窗交互习惯。
- UI 风格保持简洁稳定，文案优先中文；涉及 i18n 同步中英文资源。
- 环境变量、请求基地址、统一响应解析需与后端 `ApiResponse` 保持一致。
- 修改后至少保证构建可通过；若路由、接口字段、交互流程变化，需同步更新相关文档。
