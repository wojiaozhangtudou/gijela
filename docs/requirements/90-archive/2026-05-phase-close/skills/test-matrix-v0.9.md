# Skills 升级测试矩阵 v0.9

## 1. 目标
确保 skills 升级在契约、功能、稳定性、性能、安全方面可发布。

## 2. 契约测试
| 用例ID | 场景 | 期望 |
|---|---|---|
| CT-01 | success=true | error 为 null，meta 必填 |
| CT-02 | success=false | error.code/message 必填 |
| CT-03 | empty result | meta.empty=true（可选） |
| CT-04 | skillId/version 缺失 | 判定失败 |

## 3. 功能回归（核心技能）
| 用例ID | 技能 | 场景 | 期望 |
|---|---|---|---|
| FR-01 | knowledge.search | 正常检索 | 返回 hits，主回复继续 |
| FR-02 | knowledge.search | 空检索 | 不报错，主回复继续 |
| FR-03 | attachment.context | 有会话+有附件 | 返回 items/contextText |
| FR-04 | attachment.context | 无 sessionId | 返回可解释结果，不抛异常 |

## 4. 稳定性测试
| 用例ID | 场景 | 期望 |
|---|---|---|
| ST-01 | 上游超时 | 命中超时策略，主回复不中断 |
| ST-02 | 连续失败 | 熔断生效，半开恢复 |
| ST-03 | 重试场景 | 最多重试 N 次，不风暴 |
| ST-04 | 降级场景 | 输出降级提示并继续回答 |

## 5. 性能测试
| 用例ID | 场景 | 期望 |
|---|---|---|
| PF-01 | 50 QPS / 200 并发 | 错误率 <=1% |
| PF-02 | 技能路由开销 | P95 <= 30ms |
| PF-03 | 单技能调用 | timeout 默认 3s 生效 |

## 6. 安全测试
| 用例ID | 场景 | 期望 |
|---|---|---|
| SC-01 | 未授权技能调用 | 被拒绝并审计 |
| SC-02 | 越权脚本访问文件 | 被沙箱拦截 |
| SC-03 | 非白名单网络访问 | 被拦截 |
| SC-04 | 审计字段检查 | tenant/session/skill/version/in/out 哈希完整 |

## 7. 发布闸门
- 契约测试通过率 = 100%
- 核心回归通过率 = 100%
- 压测达标（50 QPS/200 并发）
- 灰度错误率 <= 1%，P95 达标
- 回滚演练时长 <= 10 分钟
