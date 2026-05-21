---
applyTo: "gijela-core/gijela-core-pistil/**/*"
---

# pistil 后端模块定制说明

- 当前匹配范围为 `gijela-core/gijela-core-pistil/`，该模块是后台管理主业务模块。
- 优先复用现有主链路：认证登录、用户/角色/菜单/部门/岗位、审计日志、权限聚合与缓存刷新。
- 接口风格优先沿用现有 `/api/v1/**` 与统一 `ApiResponse` / `ErrorCode`。
- 涉及 RBAC 时，优先复用既有菜单权限标识、角色菜单关系、用户角色关系及相关 service，不平行新建权限体系。
- 相关改动应保持与 `gijela-core-security-common` 的 JWT 与鉴权链路兼容。
- Mapper、Service、Controller 命名及层次保持一致，避免在 controller 直接堆业务逻辑。
- 说明、注释和接口文档优先中文，便于与现有文档风格一致。
- 若改动影响接口、权限、缓存键、登录流程或菜单树结构，需同步更新前端与文档说明。
