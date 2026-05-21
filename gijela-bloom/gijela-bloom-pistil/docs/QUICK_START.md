## 快速上手 & 常见问题速查

本文档是为本项目准备的 60 秒快速上手与常见问题（尤其是跨域 CORS、登录 token 与动态路由）排查指南，面向下次接手或复盘的开发者。

### 1. 环境准备
- 推荐包管理：pnpm
- 安装依赖并启动开发服务器：
```powershell
pnpm install
pnpm dev
```
- 若需要连接本地后端，请在仓库根创建 `.env` 或设置环境变量：
  - VITE_API_BASE=http://127.0.0.1:9006 （生产环境可设置真实后端）

### 2. 解决开发时的跨域（CORS）
- 用 Vite 的 dev proxy 在本地绕过浏览器 CORS（我们在 `vite.config.ts` 中配置）：
  - 前端统一使用相对 base `/api`（或由 `VITE_API_BASE` 覆盖），Vite 将 `/api` 转发到后端地址。
  - 优点：浏览器请求同源到 Vite，再由 Vite 转发到后端，避免后端配置临时修改。

常见问题：若你在浏览器看到 preflight 错误或 Network 中没有 `Access-Control-Allow-Origin`，请先确认是否在 dev 使用代理或后端启用了 CORS。

### 3. axios / API 客户端要点 (`src/api/client.ts`)
- baseUrl 策略：
  - 开发：使用相对 base `/api`，文件中会优先使用 `import.meta.env.VITE_API_BASE`，否则 fallback 为 `/api`。
  - 生产：设置 `VITE_API_BASE` 为后端地址。
- 避免重复前缀：不要同时在 baseUrl 与接口 path 中都写 `/api`，会产生 `/api/api/...`。

### 4. 登录与 token 处理（重要）
- 登录函数返回要兼容多种后端返回格式：
  - body 里可能是 `data.token`、`data.access_token`、或直接 `token`；也可能仅在响应头 `Authorization: Bearer <token>`。
  - 我们在 `src/store/user.ts` 的 `login` 中做了鲁棒解析：检查 body 的多个字段，再检查 headers。
- 成功拿到 token 后写入 localStorage（或按需求改为 cookie），并由 axios 请求拦截器自动带上 `Authorization: Bearer <token>`。

### 5. 菜单与动态路由（如何生效）
- 菜单由后端接口 `GET /api/v1/menus/tree` 返回 `MenuNode[]`。
- 前端通过 `menuNodesToRoutes` 将菜单转换为 `RouteRecordRaw` 并用 `router.addRoute` 注册为 `MainLayout` 的子路由（见 `src/router/index.ts`）。
- 侧栏渲染在 `src/components/SideMenu.vue`，注意侧栏 item 的 `index` 必须是以 `/` 开头的路径（例如 `/sys/roles`），否则 router 无法正确匹配与导航。

### 6. 已实现的页面（可直接访问）
- 登录：`/login`
- 首页（仪表盘）：`/dashboard`（顶栏显示为“首页”）
- 主要管理页面（示例路径）:
  - 用户管理：`/system/users` 或别名 `/sys/users`
  - 角色管理：`/system/roles` 或 `/sys/roles`
  - 菜单管理：`/system/menus` 或 `/sys/menus`
  - 审计日志：`/system/audit` 或 `/sys/audit`
  - 部门管理：`/org/dept`
  - 岗位管理：`/org/post`

### 7. 常见故障排查清单（快速）
1. 点击菜单无反应：打开 `Network`，确认是否请求了 `/v1/menus/tree` 并且菜单节点的 `path` 有无前导 `/`。
2. 页面路由 404：检查 `src/router/index.ts` 中是否已注册该路由（静态或动态）。动态路由只有在登录后并且 `loadInitialData()` 成功时注册。
3. 登录成功但前端未保存 token：在 `login` 里打印 `resp`，查看 token 在哪里（body / header），并检查 `localStorage`。
4. 出现 `/api/api`：检查接口调用处是否在 path 与 base 都使用了 `/api`。

### 8. 常用文件速查
- API 客户端入口：`src/api/client.ts`
- 各资源 API：`src/api/user.ts, role.ts, menu.ts, dept.ts, post.ts, audit.ts`
- 状态（token/login/menus）：`src/store/user.ts`
- 路由与动态路由：`src/router/index.ts`
- 布局与侧栏：`src/layout/MainLayout.vue`, `src/components/SideMenu.vue`
- 页面：`src/views/*`, `src/views/system/*`

### 9. 下一步建议（短期）
- 使用 OpenAPI 自动生成类型化 API 客户端（`api-docs.yaml` 可用于生成）。RAG 前端由独立模块承载，不在当前模块内混用接口客户端。
- 为菜单到页面的映射建立配置文件（避免在代码中使用正则匹配）。
- 为按钮级权限实现 `<HasPermission />` 指令或组件（利用 menu node 的 `permission` 字段）。

---

如需我把这个文档拆成更细的步骤（比如 `docs/DEV_PROXY.md`、`docs/API_CLIENT.md`），或直接开始用 `api-docs.yaml` 自动生成客户端，请告诉我下一步优先级。
