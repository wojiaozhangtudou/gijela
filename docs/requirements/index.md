# 需求文档索引

## 当前状态
- 当前阶段：chat-flow Phase 1 **已完成归档**，可启动 Phase 2 规划
- 实现状态：
  - ✅ Phase 1 全量交付：后端会话/消息/Completion API + 前端三栏工作台
  - ✅ 运行历史区分（debug/prod）+ DB 层会话过滤（`trigger_by`）
  - ✅ CHAT/TRANSFORM 模式语义强化
  - ✅ 前后端编译验证通过
- 归档：[90-archive/2026-05-chatflow-phase1](./90-archive/2026-05-chatflow-phase1/README.md)
- [02-backlog](./02-backlog/)：待排期需求池
- [03-decisions](./03-decisions/)：跨需求决策（ADR）
- [04-bugs](./04-bugs/)：缺陷修复与问题跟踪
- [90-archive](./90-archive/)：历史归档（只读，[批次总览](./90-archive/README.md)）

## 常用模板
- [REQ 模板](./00-governance/REQ-TEMPLATE.md)
- [ARCH-REVIEW 模板](./00-governance/ARCH-REVIEW-TEMPLATE.md)
- [BUG 模板](./00-governance/BUG-TEMPLATE.md)
- [ADR 模板](./00-governance/ADR-TEMPLATE.md)

## 常用示例
- [需求示例](./00-governance/examples/REQ-EXAMPLE.md)
- [评审示例](./00-governance/examples/ARCH-REVIEW-EXAMPLE.md)
- [缺陷示例](./00-governance/examples/BUG-EXAMPLE.md)

## 使用顺序
1. 新想法先进入 [02-backlog](./02-backlog/)。
2. 确认排期后，在 [01-active](./01-active/) 建需求目录，并优先复制 [REQ 模板](./00-governance/REQ-TEMPLATE.md)。
3. 需要落地评审时，补 [ARCH-REVIEW 模板](./00-governance/ARCH-REVIEW-TEMPLATE.md) 文档。
4. 进入修复期后，在 [04-bugs](./04-bugs/) 维护 [BUG 总表](./04-bugs/BUG-LIST.md) 与对应 `BUG-*.md`。
5. 长期有效的跨需求决策，沉淀到 [03-decisions](./03-decisions/)。
6. 阶段结束后，统一迁移到 [90-archive](./90-archive/)。

## 统一维护原则
1. 文档默认中文，保持“可执行、可验证、可维护”。
2. 需求文档必须标明影响模块，保持前后端与安全模块边界清晰。
3. 接口、权限、缓存、菜单、登录流程有变更时，需求文档与实现说明同步更新。
4. `index.md` 只保留导航、状态、最新评审入口，不再堆积历史清单。

## 本次归档
- 归档批次：2026-05-phase-close
- 归档位置：[90-archive/2026-05-phase-close](./90-archive/2026-05-phase-close/)
- 说明：历史 ARCH-REVIEW / SPEC / DEV-FEEDBACK / REQ 及阶段文档已统一迁移。
- 批次说明：[README](./90-archive/2026-05-phase-close/README.md)

## 增量归档
- 归档批次：2026-05-bugfix-close
- 归档位置：[90-archive/2026-05-bugfix-close](./90-archive/2026-05-bugfix-close/)
- 说明：本轮修复需求 `REQ-20260509-001` 已完成并迁移归档。
- 批次说明：[README](./90-archive/2026-05-bugfix-close/README.md)

- 归档批次：2026-05-20-avatar-upload-422-fix
- 归档位置：[90-archive/2026-05-20-avatar-upload-422-fix](./90-archive/2026-05-20-avatar-upload-422-fix/)
- 说明：用户头像上传 422 问题评审文档已归档（ARCH-REVIEW-20260520-002）。
- 批次说明：[README](./90-archive/2026-05-20-avatar-upload-422-fix/README.md)

- 归档批次：2026-05-21-chat-model-config-review-close
- 归档位置：[90-archive/2026-05-21-chat-model-config-review-close](./90-archive/2026-05-21-chat-model-config-review-close/)
- 说明：chat 模块 LLM / Embedding 模型配置数据库化评审文档已归档（ARCH-REVIEW-20260520-003）。
- 批次说明：[README](./90-archive/2026-05-21-chat-model-config-review-close/README.md)

## 最新评审
- [ARCH-REVIEW-20260520-001：chat-flow 评审文档整合与 Phase 2 开发口径评审](./ARCH-REVIEW-20260520-001.md)
- Phase 2 开发入口（2A/2B 任务拆分）见上文第六章。
- 历史架构评审归档：[90-archive/2026-05-20-arch-review-consolidation](./90-archive/2026-05-20-arch-review-consolidation/README.md)
- 模型配置评审归档：[90-archive/2026-05-21-chat-model-config-review-close](./90-archive/2026-05-21-chat-model-config-review-close/README.md)
- 历史评审与开发反馈详见归档：[90-archive/2026-05-doc-governance](./90-archive/2026-05-doc-governance/README.md)

## 最新开发进展
- 2026-05-20：已落地 chat 模块模型配置管理 MVP（数据库表 `chat_model_config`、后端 CRUD 接口、前端“模型配置管理”页面、新建会话/知识入库模型选项联动）。
- 2026-05-20（续）：聊天主链路与知识入库 embedding 已切换为数据库配置读取。
- 当前待完成：图谱抽取与附件摘要链路的运行时模型配置切换（详见 [ARCH-REVIEW-20260520-003](./90-archive/2026-05-21-chat-model-config-review-close/ARCH-REVIEW-20260520-003.md) 第六章反馈）。

## 当前活跃需求
- 当前无活跃需求（请从 02-backlog 立项后再迁入 01-active）。

## 当前缺陷跟踪
- 当前无活跃缺陷（详见 [BUG-LIST.md](./04-bugs/BUG-LIST.md)）

## 后续维护规则（简版）
1. 新需求先落 02-backlog，再晋升到 01-active。
2. 每个活跃需求仅保留一个主文件：REQ-YYYYMMDD-序号.md。
3. 评审文档统一命名：ARCH-REVIEW-YYYYMMDD-序号.md。
4. 已完成需求若进入修复期，在 04-bugs 挂 BUG 文档与聚合清单。
5. 需求关闭后，按批次迁移到 90-archive/YYYY-MM-批次名。
6. index.md 只保留"导航+状态"，不再堆全量历史列表。
