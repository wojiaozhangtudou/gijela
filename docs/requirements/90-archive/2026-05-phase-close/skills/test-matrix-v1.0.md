# Skills 升级测试矩阵 v1.0（冻结）

- 状态：Frozen
- 冻结日期：2026-04-30
- 适用范围：`knowledge.search`、`attachment.context` 与后续 SkillProvider 体系

## 1. 目标
确保 skills 升级在契约、功能、稳定性、性能、安全、发布门槛上可验收、可回滚。

## 2. 责任分配（Owner）
| 角色 | 责任 |
|---|---|
| BE | 契约实现、超时/重试/熔断/降级、审计字段 |
| FE | ToolResult 展示适配与失败降级文案 |
| QA | 契约/回归/故障注入/压测执行 |
| OPS | 灰度发布、回滚演练、监控阈值 |
| SEC | 沙箱与白名单、秘钥 scope、合规检查 |

## 3. 契约测试（CT）
| 用例ID | Owner | 场景 | 期望 |
|---|---|---|---|
| CT-01 | QA+BE | `success=true` | `error=null`，`meta.skillId/version/latencyMs` 必填 |
| CT-02 | QA+BE | `success=false` | `data=null`，`error.code/message` 必填 |
| CT-03 | QA+BE | 空结果 | 允许 `meta.empty=true` |
| CT-04 | QA+BE | 缺少必填字段 | 判定失败并拦截发布 |

## 4. 功能回归（FR）
| 用例ID | Owner | 技能 | 场景 | 期望 |
|---|---|---|---|---|
| FR-01 | QA+BE | knowledge.search | 正常检索 | 返回 hits，主回复继续 |
| FR-02 | QA+BE | knowledge.search | 空检索 | 不报错，主回复继续 |
| FR-03 | QA+BE | attachment.context | 有会话+有附件 | 返回 items/contextText |
| FR-04 | QA+BE | attachment.context | 无 sessionId | 返回可解释结果，不抛异常 |
| FR-05 | QA+FE | 全局 | ToolResult 前端展示 | 字段映射正确、无原始协议泄漏 |

## 5. 稳定性测试（ST）
| 用例ID | Owner | 场景 | 期望 |
|---|---|---|---|
| ST-01 | QA+BE | 上游超时 | 命中超时策略，主回复不中断 |
| ST-02 | QA+BE | 连续失败 | 熔断生效，半开探测恢复 |
| ST-03 | QA+BE | 可重试错误 | 最多重试 2 次（指数退避） |
| ST-04 | QA+BE | 降级路径 | 技能失败但主回复继续 |

## 6. 性能测试（PF）
| 用例ID | Owner | 场景 | 期望 |
|---|---|---|---|
| PF-01 | QA+OPS | 50 QPS / 200 并发 | 错误率 <= 1% |
| PF-02 | QA+BE | 技能路由开销 | P95 <= 30ms |
| PF-03 | QA+BE | 单技能调用 | 默认超时 3s 生效 |

## 7. 安全测试（SC）
| 用例ID | Owner | 场景 | 期望 |
|---|---|---|---|
| SC-01 | SEC+QA | 未授权技能调用 | 被拒绝并审计 |
| SC-02 | SEC+QA | 越权脚本访问文件 | 被沙箱拦截 |
| SC-03 | SEC+QA | 非白名单网络访问 | 被拦截 |
| SC-04 | SEC+QA | 审计字段检查 | tenant/session/skill/version/in/out 哈希完整 |

## 8. CI 闸门映射
| 闸门 | 来源用例 | 判定 |
|---|---|---|
| G1 契约闸门 | CT-01~CT-04 | 通过率=100% |
| G2 回归闸门 | FR-01~FR-05 | 通过率=100% |
| G3 稳定性闸门 | ST-01~ST-04 | 全部通过 |
| G4 性能闸门 | PF-01~PF-03 | 全部达标 |
| G5 安全闸门 | SC-01~SC-04 | 全部通过 |

## 9. 发布闸门（Go/No-Go）
- Go（全部满足）：
  1. G1~G5 全绿
  2. 灰度错误率 <= 1%
  3. 回滚演练 <= 10 分钟
- No-Go（任一触发）：
  1. 错误率 > 1% 持续 5 分钟
  2. P95 超阈持续 5 分钟
  3. 核心技能链路回归失败
