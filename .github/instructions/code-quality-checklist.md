---
applyTo: "gijela-core/**/*.java"
---

# 代码质量规约与常见错误

- 当前规约基于 Java 开发手册标准，要求所有 gijela-core 模块遵守。
- 编码红线（强制）：单行 ≤ 120 字符、异常必 logger.error()、无敏感信息硬编码、无魔数。
- 编码推荐：方法 ≤ 80 行、职责单一、链式调用分层、配置参数化、命名一致性（Service/DAO 实现类使用 Impl；Validator 使用"策略语义 + Validator"，不使用 Impl）。
- 常见错误类别：超长行、敏感信息硬编码、魔数硬编码、异常处理不完整、方法过长职责混杂、菜单逻辑混杂、链式调用难维护、配置硬编码、命名不一致。
- 提交前运行：`mvn -pl gijela-core-pistil -am -DskipTests compile` 验证无编译错误。
- 代码变更若涉及接口、权限、缓存键、菜单等，需同步更新 `.github/requirements-workflow.md` 中对应的需求文档。
- 建议启用自动检查：`./scripts/install-git-hooks.ps1`（提交前自动校验规范是否同步）。
