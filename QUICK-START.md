# 快速开始

欢迎加入 gijela 仓库协作！本指南帮你快速上手。

## 仓库结构速览
```
gijela/
├── .github/                 ← 仓库协作规范与工作流
│   ├── copilot-instructions.md     ← 全局规则
│   ├── instructions/               ← 模块级细则
│   └── agents/                     ← 助手任务定义
├── gijela-core/             ← 后端（Spring Boot + Maven）
├── gijela-bloom/            ← 前端（Vue3 + Vite）
└── docs/requirements/       ← 需求、评审、缺陷、决策文档
```

## 我要做什么？

### 新功能 / 改进 / 想法
1. 进入 [需求池](docs/requirements/02-backlog/)，按 `README` 补充想法。
2. 排期后，在 [活跃需求](docs/requirements/01-active/) 建目录，按 [REQ 模板](docs/requirements/00-governance/REQ-TEMPLATE.md) 编写。
3. 需要评审时，补 [ARCH-REVIEW](docs/requirements/00-governance/ARCH-REVIEW-TEMPLATE.md) 文档。

### 遇到 Bug
1. 进入 [缺陷跟踪](docs/requirements/04-bugs/)，按 [BUG 模板](docs/requirements/00-governance/BUG-TEMPLATE.md) 编写。
2. 更新 [BUG-LIST.md](docs/requirements/04-bugs/BUG-LIST.md)。

### 代码规范
- 后端：[.github/instructions/pistil-backend.instructions.md](.github/instructions/pistil-backend.instructions.md)
- 前端：[.github/instructions/pistil-frontend.instructions.md](.github/instructions/pistil-frontend.instructions.md)
- 安全：[.github/instructions/security.instructions.md](.github/instructions/security.instructions.md)

### 协作流程
1. 阅读 [.github/copilot-instructions.md](.github/copilot-instructions.md)（全局规则）。
2. 查看模块特定指南（上面列出的 instructions）。
3. 遵循文档治理：所有需求/评审/缺陷必须落在 `docs/requirements/`，不在根目录散放。

## 文档导航
- **快速参考**：[docs/requirements/index.md](docs/requirements/index.md)
- **工作流指南**：[.github/requirements-workflow.md](.github/requirements-workflow.md)
- **文档治理规则**：[docs/requirements/00-governance/README.md](docs/requirements/00-governance/README.md)
- **归档浏览**：[docs/requirements/90-archive/README.md](docs/requirements/90-archive/README.md)

## 遇到问题？
- 疑问不属于需求/Bug？进入 [03-decisions](docs/requirements/03-decisions/) 记录决策。
- 需要历史背景？按批次查阅 [90-archive](docs/requirements/90-archive/)。
