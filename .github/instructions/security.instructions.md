---
applyTo: "gijela-core/gijela-core-security-common/**/*"
---

# 安全模块额外说明

- 当前匹配范围为 `gijela-core/gijela-core-security-common/`。
- 该模块是 JWT、鉴权上下文、权限表达式、公共异常与响应结构的基础模块。
- 重点保持：统一返回体、统一异常、JWT 配置与校验、Redis 权限/版本校验约定、方法级权限表达式能力。
- 不要随意改变 token 声明字段、认证过滤链顺序、错误码语义、缓存 key 规则；如必须调整，优先兼容并同步文档。
- 安全改动应与 `gijela-core-pistil` 的登录、刷新权限、用户上下文链路联动验证。
