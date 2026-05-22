# gijela-core-chat 操作手册

> 发布版文档，适用于联调开发、测试验证、功能演示与日常排障。

## 封面摘要

如果你是第一次接触 `gijela-core-chat`，可以先看这一段：

- 这是什么：一套围绕大模型联调、知识检索、图谱抽取、附件处理、Skill、MCP 与日志排查的综合工作台
- 适合谁看：联调开发、测试同学、演示人员、接手维护的同学
- 读完能做什么：知道各页面入口、理解每个模块的用途、按顺序跑通主要联调链路、遇到问题时知道优先去哪排查

建议阅读顺序：

1. 先看“快速上手”，了解最短验证路径。
2. 再看“联调工作台”，掌握主入口怎么使用。
3. 按需查看知识库、图谱、附件、MCP、模型配置、提示词和日志看板。
4. 遇到问题时，最后回看“推荐联调路径”和“常见问题”。

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0.0 |
| 更新时间 | 2026-05-22 |
| 适用模块 | `gijela-core-chat` + `gijela-bloom-chat` |
| 前端地址 | `http://localhost:5174/` |
| 文档定位 | 面向使用者的图文操作说明 |

## 目录

- [0. 使用说明](#usage-notice)
- [1. 文档说明](#doc-intro)
- [2. 系统访问](#access)
- [3. 快速上手](#quick-start)
- [4. 联调工作台](#chat-workbench)
- [5. 知识库管理](#knowledge)
- [6. 图谱管理](#graph)
- [7. 附件管理](#attachments)
- [8. 对象存储管理](#storage)
- [9. 技能管理](#skills)
- [10. MCP 管理](#mcp)
- [11. 模型配置管理](#model-config)
- [12. 提示词管理](#prompts)
- [13. LLM 日志看板](#llm-logs)
- [14. 推荐联调路径](#suggested-path)
- [15. 常见问题](#faq)

---

<a id="usage-notice"></a>

## 0. 使用说明

为便于阅读和复现，这里先说明本文的使用边界：

- 截图环境：本地联调环境 `http://localhost:5174/`
- 页面数据：均来自当前本地已存在的测试数据、模型配置、会话数据与日志记录
- 操作目标：用于说明“页面怎么用”和“联调时先看哪里”，不是后端接口设计文档
- 推荐读者：联调开发、测试同学、演示使用者、接手维护同学

使用本文时，建议注意以下事项：

1. 某些页面是否有数据，取决于本地是否已经初始化数据库、模型配置、知识集合、图谱空间、附件数据等。
2. 部分操作依赖外部组件可用，例如模型服务、Qdrant、Neo4j、对象存储、MCP Server。
3. 如果页面能打开但无数据，优先不要怀疑前端，可先检查对应后端依赖、配置和初始化数据。
4. 本文中的截图重点是说明入口和操作位置，实际联调结果会随着本地数据变化而变化。

---

<a id="doc-intro"></a>

## 1. 文档说明

这份手册用于说明 `gijela-core-chat` 的主要页面、典型操作和联调入口，适合以下场景：

- 初次接触项目，快速熟悉页面结构
- 做大模型、知识库、图谱、附件、MCP、技能等能力联调
- 做测试回归或演示时，按页面快速定位功能
- 排查“配置存在但页面不会用”这类问题

> 本文截图均来自本地联调环境 `http://localhost:5174/`。

---

<a id="access"></a>

## 2. 系统访问

访问地址：`http://localhost:5174/`

进入后默认首页为“大模型联调工作台”，这里是整套 `chat` 能力的总入口。

<p align="center">
  <img src="./assets/chat/01-chat-home.png" alt="大模型联调工作台" width="920" />
</p>
<p align="center"><em>图 2-1 联调工作台首页</em></p>

---

<a id="quick-start"></a>

## 3. 快速上手

如果你只想先跑通一遍主链路，建议按下面顺序体验：

1. 进入联调工作台，点击“新建会话”创建会话。
2. 选择对话模型，例如 `qwen-plus`。
3. 输入问题，分别体验“同步发送”和“流式发送”。
4. 如需验证附件链路，先选中会话，再上传文件。
5. 如需验证知识检索链路，进入“知识库管理”写入文本后再回到工作台提问。
6. 如需验证运维侧能力，进入“模型配置”“MCP 管理”“技能管理”“日志看板”查看状态。

如果你是第一次联调，推荐先准备以下最小条件：

- 至少一条可用的对话模型配置
- 至少一个可切换的测试会话
- 如需验证 RAG，先准备一个知识集合和一段测试文本
- 如需验证附件链路，先准备一个可上传的测试文件
- 如需验证 MCP，先准备一个可连通的 MCP Server

---

<a id="chat-workbench"></a>

## 4. 联调工作台

联调工作台是 `gijela-core-chat` 的主入口，页面包含以下核心区域：

- 顶部快捷入口：知识库、图谱、附件、存储、技能、MCP、模型配置、提示词、日志看板
- 左侧会话列表：支持创建、切换、编辑、删除会话
- 左侧附件上传：附件与当前会话绑定
- 右侧消息窗口：展示对话消息、流式输出、工具调用结果
- 底部输入区：选择模型、输入问题、同步发送、流式发送

### 4.1 新建会话

点击“新建会话”后，可填写会话名称，并指定该会话默认使用的大模型。

<p align="center">
  <img src="./assets/chat/02-chat-new-session-dialog.png" alt="新建会话弹窗" width="920" />
</p>
<p align="center"><em>图 4-1 新建会话弹窗</em></p>

会话列表中的“编辑”按钮可直接修改会话名称和默认模型。

<p align="center">
  <img src="./assets/chat/21-chat-edit-session-dialog.png" alt="编辑会话弹窗" width="920" />
</p>
<p align="center"><em>图 4-2 编辑会话弹窗</em></p>

### 4.2 工作台常用操作

建议重点体验以下能力：

- `新建会话`：为不同主题拆分联调上下文
- `当前发送模型`：切换模型后再发送，方便对比模型表现
- `同步发送`：适合快速验证接口是否可用
- `流式发送`：适合验证流式链路、逐段输出、工具调用过程
- `仅清空当前窗口`：只清空消息，不删除会话
- `仅失败` 开关：快速定位失败会话
- `选择文件`：为当前会话上传附件，验证附件上下文链路

下面两张截图分别展示了同步发送和流式发送后的典型工作台效果：

<p align="center">
  <img src="./assets/chat/29-chat-sync-response.png" alt="同步发送响应示意" width="920" />
</p>
<p align="center"><em>图 4-3 同步发送响应示意</em></p>

<p align="center">
  <img src="./assets/chat/30-chat-stream-response.png" alt="流式发送响应示意" width="920" />
</p>
<p align="center"><em>图 4-4 流式发送响应示意</em></p>

### 4.3 适合验证的主链路

你可以在这个页面直接验证以下能力是否通畅：

- 基础问答
- 多会话切换
- 模型切换
- 附件上传与会话绑定
- 知识库检索引用
- 流式响应
- 工具调用 / Skill / MCP 集成后的回答效果

### 4.4 使用建议

- 日常联调优先使用“流式发送”，更容易看清响应过程。
- 多模型对比时，建议每次只切换一个模型并保留同一条提问。
- 如果当前会话上下文已混乱，优先新建会话，而不是继续在旧会话上叠加测试。
- 如果只是想重试回答，不想删除会话，可使用“仅清空当前窗口”。

---

<a id="knowledge"></a>

## 5. 知识库管理

路径：`/knowledge`

该页面负责知识写入、向量集合初始化、检索结果查看，是验证 RAG 链路的重要页面。

主要功能包括：

- 创建或校验向量集合
- 自动探测向量维度
- 文本写入知识库
- 文件写入知识库
- 设置分块大小与重叠长度
- 输入检索问题并查看命中结果
- 管理集合、删除集合、清空向量、删除单个或批量向量

<p align="center">
  <img src="./assets/chat/03-knowledge-page.png" alt="知识库管理页面" width="920" />
</p>
<p align="center"><em>图 5-1 知识库管理页面</em></p>

<p align="center">
  <img src="./assets/chat/04-knowledge-manage-dialog.png" alt="集合与向量管理弹窗" width="920" />
</p>
<p align="center"><em>图 5-2 集合与向量管理弹窗</em></p>

### 推荐操作

1. 先选择向量模型。
2. 点击“创建/校验集合”。
3. 用“文本写入”或“文件写入”导入测试数据。
4. 在右侧输入检索问题，确认能否命中正确内容。
5. 如需清理测试数据，打开“集合/向量管理”执行删除操作。

建议：

- 初次验证时优先使用“文本写入”，更容易确认入库内容与检索结果是否一致。
- 若检索结果不理想，优先检查向量模型、分块大小、检索问题是否过于宽泛。

---

<a id="graph"></a>

## 6. 图谱管理

路径：`/graph`

该页面用于验证图谱抽取、预览确认导入、实体列表和关系列表等能力。

主要功能包括：

- 图谱空间创建与删除
- 文本抽取预览
- 文件抽取预览
- 选择抽取模式和导入模式
- 指定图谱抽取模型
- 确认入图、刷新列表、重布局
- 查看实体详情、关系详情
- 删除选中实体或关系

<p align="center">
  <img src="./assets/chat/05-graph-page.png" alt="图谱管理页面" width="920" />
</p>
<p align="center"><em>图 6-1 图谱管理页面</em></p>

### 推荐操作

1. 先选择或创建 `graphSpace`。
2. 输入文本或选择文件做抽取预览。
3. 检查实体与关系预览结果。
4. 点击“确认入图”。
5. 再检查下方实体列表和关系列表是否已入库。

建议：

- 做首轮验证时，先用一小段结构清晰的文本，不要一开始就上传过大的文件。
- 如果抽取结果偏差较大，优先检查抽取模型、抽取模式与原始文本质量。

---

<a id="attachments"></a>

## 7. 附件管理

路径：`/attachments`

该页面用于按会话查看附件，支持上传、检索、下载、重命名、删除和查看摘要详情。

主要功能包括：

- 选择会话后查看附件列表
- 按文件名、上传时间、状态筛选
- 上传附件
- 下载附件
- 重命名附件
- 删除单个或批量附件
- 查看附件摘要、浓缩 Markdown、原文文本

<p align="center">
  <img src="./assets/chat/06-attachments-page.png" alt="附件管理页面" width="920" />
</p>
<p align="center"><em>图 7-1 附件管理页面</em></p>

<p align="center">
  <img src="./assets/chat/20-attachments-detail-dialog.png" alt="附件详情弹窗" width="920" />
</p>
<p align="center"><em>图 7-2 附件详情弹窗</em></p>

<p align="center">
  <img src="./assets/chat/22-attachments-rename-dialog.png" alt="附件重命名弹窗" width="920" />
</p>
<p align="center"><em>图 7-3 附件重命名弹窗</em></p>

<p align="center">
  <img src="./assets/chat/23-attachments-delete-confirm.png" alt="附件删除确认弹窗" width="920" />
</p>
<p align="center"><em>图 7-4 附件删除确认弹窗</em></p>

### 推荐操作

1. 先选择会话。
2. 上传测试文件。
3. 刷新后查看处理状态是否从“待处理/处理中”变为“已完成”。
4. 打开详情页检查摘要与原文是否正确。

建议：

- 附件测试尽量使用内容明确、文本可提取的文件。
- 若状态长期停留在处理中，可去日志页和后端日志中定位处理异常。

---

<a id="storage"></a>

## 8. 对象存储管理

路径：`/storage`

该页面用于验证对象存储链路，适合检查桶管理、对象上传下载、删除是否正常。

主要功能包括：

- 列出桶列表
- 新建桶
- 删除空桶
- 查询对象列表
- 上传对象
- 下载对象
- 删除对象

<p align="center">
  <img src="./assets/chat/07-storage-page.png" alt="对象存储管理页面" width="920" />
</p>
<p align="center"><em>图 8-1 对象存储管理页面</em></p>

### 推荐操作

1. 先创建一个测试桶。
2. 进入桶后上传文件。
3. 通过对象列表验证上传结果。
4. 再测试下载和删除流程。

建议：

- 对象存储更适合验证基础文件链路，不建议直接把它等同于“附件摘要处理结果”。
- 如果上传失败，优先检查桶名、对象存储配置、服务连通性。

---

<a id="skills"></a>

## 9. 技能管理

路径：`/skills`

该页面用于查看当前已加载的 Skill，并支持启停和热重载。

主要功能包括：

- 查看技能名称、来源、版本、启用状态、最近错误
- 启用或禁用某个技能
- 手动热重载本地技能目录
- 打开详情查看入参 Schema 和 `SKILL.md`

<p align="center">
  <img src="./assets/chat/08-skills-page.png" alt="技能管理页面" width="920" />
</p>
<p align="center"><em>图 9-1 技能管理页面</em></p>

<p align="center">
  <img src="./assets/chat/17-skills-detail-dialog.png" alt="技能详情弹窗" width="920" />
</p>
<p align="center"><em>图 9-2 技能详情弹窗</em></p>

### 推荐操作

1. 先看“最近错误”列是否有加载失败提示。
2. 点击“热重载”检查技能目录是否可重新加载。
3. 随机打开一个技能详情，确认 Schema 和清单内容是否正常。

建议：

- 做技能调试时，先从“详情”和“最近错误”入手，比直接猜测问题更高效。
- 热重载后若无变化，应重点检查本地技能目录和清单格式。

---

<a id="mcp"></a>

## 10. MCP 管理

路径：`/mcp-servers`

该页面用于维护外部 MCP Server，是验证工具协议接入的关键页面。

主要功能包括：

- 新增 MCP Server
- 编辑 MCP Server
- 删除 MCP Server
- 启用 / 禁用 MCP Server
- 测试连通性
- 查看详情与工具数
- 支持 `streamable_http`、`sse`、`stdio` 三种 transport

<p align="center">
  <img src="./assets/chat/09-mcp-page.png" alt="MCP 管理页面" width="920" />
</p>
<p align="center"><em>图 10-1 MCP 管理页面</em></p>

<p align="center">
  <img src="./assets/chat/18-mcp-create-dialog.png" alt="新增 MCP Server 弹窗" width="920" />
</p>
<p align="center"><em>图 10-2 新增 MCP Server 弹窗</em></p>

<p align="center">
  <img src="./assets/chat/27-mcp-edit-dialog.png" alt="编辑 MCP Server 弹窗" width="920" />
</p>
<p align="center"><em>图 10-3 编辑 MCP Server 弹窗</em></p>

<p align="center">
  <img src="./assets/chat/24-mcp-detail-dialog.png" alt="MCP 详情弹窗" width="920" />
</p>
<p align="center"><em>图 10-4 MCP 详情弹窗</em></p>

### 推荐操作

1. 新增一个 MCP Server。
2. 填写 transport、endpoint 或 command。
3. 保存后点击“测试”。
4. 查看状态、状态消息和工具数是否符合预期。

建议：

- 初次验证时优先使用最简单、最稳定的 transport。
- 测试失败时，先看状态消息，再回头检查 endpoint、token、command 等配置项。

---

<a id="model-config"></a>

## 11. 模型配置管理

路径：`/model-configs`

该页面用于统一维护对话模型和向量模型配置。

主要功能包括：

- 新增配置
- 编辑配置
- 删除配置
- 过滤查看全部 / 对话模型 / 向量模型
- 测试模型配置是否可用

<p align="center">
  <img src="./assets/chat/10-model-configs-page.png" alt="模型配置管理页面" width="920" />
</p>
<p align="center"><em>图 11-1 模型配置管理页面</em></p>

<p align="center">
  <img src="./assets/chat/19-model-config-create-dialog.png" alt="新增模型配置弹窗" width="920" />
</p>
<p align="center"><em>图 11-2 新增模型配置弹窗</em></p>

<p align="center">
  <img src="./assets/chat/25-model-config-edit-dialog.png" alt="编辑模型配置弹窗" width="920" />
</p>
<p align="center"><em>图 11-3 编辑模型配置弹窗</em></p>

### 推荐操作

1. 新增一个模型配置。
2. 填写模型名、Base URL、API Key、超时参数。
3. 保存后执行“测试”。
4. 若测试成功，再返回联调工作台切换该模型发送消息。

建议：

- 模型配置是很多联调问题的起点，若聊天、知识检索、摘要等链路异常，先回来检查这里。
- 建议把“对话模型”和“向量模型”分开维护，避免定位问题时混淆。

---

<a id="prompts"></a>

## 12. 提示词管理

路径：`/prompts`

该页面用于统一管理系统提示词和会话提示词，支持多条记录。

主要功能包括：

- 系统提示词列表查看
- 按 `appCode`、`modelRoute` 加载系统提示词
- 新增、编辑、删除系统提示词
- 按会话加载会话提示词
- 新增、编辑、删除会话提示词

<p align="center">
  <img src="./assets/chat/11-prompts-page.png" alt="提示词管理页面" width="920" />
</p>
<p align="center"><em>图 12-1 提示词管理页面</em></p>

<p align="center">
  <img src="./assets/chat/12-prompts-create-dialog.png" alt="新增系统提示词弹窗" width="920" />
</p>
<p align="center"><em>图 12-2 新增系统提示词弹窗</em></p>

<p align="center">
  <img src="./assets/chat/26-prompts-edit-dialog.png" alt="编辑提示词弹窗" width="920" />
</p>
<p align="center"><em>图 12-3 编辑提示词弹窗</em></p>

<p align="center">
  <img src="./assets/chat/28-prompts-delete-confirm.png" alt="删除提示词确认弹窗" width="920" />
</p>
<p align="center"><em>图 12-4 删除提示词确认弹窗</em></p>

### 推荐操作

1. 在“系统提示词”页签新增一条系统提示词。
2. 返回工作台验证回答风格是否受影响。
3. 再切到“会话提示词”页签，为某个会话单独增加覆盖提示词。

建议：

- 系统提示词适合做全局行为约束；会话提示词适合做局部实验。
- 联调时尽量只改一条提示词并立即验证效果，便于确认因果关系。

---

<a id="llm-logs"></a>

## 13. LLM 日志看板

日志看板入口路径：`/chat/logs/*`

当前包括 4 个页面：

- `overview`：概览
- `audit`：审计日志
- `alerts`：告警管理
- `troubleshoot`：问题排查

### 13.1 概览页

<p align="center">
  <img src="./assets/chat/13-logs-overview-page.png" alt="日志概览页面" width="920" />
</p>
<p align="center"><em>图 13-1 日志概览页面</em></p>

适合查看整体调用趋势、核心指标与快速状态。

### 13.2 审计页

<p align="center">
  <img src="./assets/chat/14-logs-audit-page.png" alt="日志审计页面" width="920" />
</p>
<p align="center"><em>图 13-2 日志审计页面</em></p>

适合查看调用记录、审计明细、问题请求线索。

### 13.3 告警页

<p align="center">
  <img src="./assets/chat/15-logs-alerts-page.png" alt="日志告警页面" width="920" />
</p>
<p align="center"><em>图 13-3 日志告警页面</em></p>

适合查看告警规则、异常状态与处理入口。

### 13.4 问题排查页

<p align="center">
  <img src="./assets/chat/16-logs-troubleshoot-page.png" alt="问题排查页面" width="920" />
</p>
<p align="center"><em>图 13-4 问题排查页面</em></p>

适合做调用异常、慢请求、失败链路排查。

使用建议：

- 先看概览判断是否整体异常，再进入审计、告警、排障页逐步下钻。
- 遇到“页面正常但结果不对”的情况，日志看板通常比反复试点页面更有效。

---

<a id="suggested-path"></a>

## 14. 推荐联调路径

如果你想完整验证一遍 `gijela-core-chat`，建议按下面顺序：

1. **模型配置管理**：先确认模型配置可测试通过。
2. **联调工作台**：创建会话并发送一条同步消息、一条流式消息。
3. **知识库管理**：导入一段知识文本，再回工作台提问验证检索链路。
4. **附件管理**：给当前会话上传附件，确认摘要和原文提取正常。
5. **图谱管理**：用文本或文件做一次抽取预览并确认入图。
6. **技能管理**：确认技能已加载，并尝试热重载。
7. **MCP 管理**：测试一台 MCP Server 的连通性。
8. **日志看板**：回到日志页查看调用记录、告警和排查信息。

这样可以较完整地覆盖：模型、对话、RAG、附件、图谱、Skill、MCP、日志这几条主链路。

---

<a id="faq"></a>

## 15. 常见问题

### Q1：页面能打开，但发送消息没有结果怎么办？

建议依次检查：

- 模型配置是否可测试通过
- 后端服务是否正常连接模型服务
- 日志看板里是否已有失败记录
- 当前会话是否选中了可用模型

### Q2：知识库写入成功，但检索不到内容怎么办？

建议检查：

- 向量集合是否已创建
- 向量模型是否正确
- 检索问题是否与写入内容明显相关
- 是否误删了集合或向量

### Q3：附件上传后一直处理中怎么办？

建议检查：

- 附件处理链路是否启动完整
- 摘要模型是否可用
- 文件内容是否可被正常提取

### Q4：MCP Server 显示异常怎么办？

建议检查：

- transport 类型是否选对
- endpoint / command 是否有效
- Bearer Token 是否配置正确
- 目标服务是否可访问

### Q5：Skill 热重载后没变化怎么办？

建议检查：

- 本地技能目录是否真的发生变化
- `SKILL.md` 是否格式正确
- 技能详情中的最近错误是否有提示

### Q6：为什么页面有入口，但列表里没有任何数据？

建议检查：

- 本地数据库是否已初始化
- 对应模块是否已有测试数据
- 相关外部依赖是否已启动
- 当前筛选条件是否过严

### Q7：联调时应该先看页面，还是先看日志？

建议原则：

- 想确认“功能入口和操作路径”时，先看页面
- 想确认“为什么失败、失败在哪一层”时，优先看日志
