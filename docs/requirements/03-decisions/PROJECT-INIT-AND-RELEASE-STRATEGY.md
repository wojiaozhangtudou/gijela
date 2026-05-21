# gijela 项目初始化与双渠道发布策略（GitHub + Gitee）

> 适用仓库：gijela  
> 当前远端：
> - GitHub: https://github.com/wojiaozhangtudou/gijela.git
> - Gitee: https://gitee.com/zhangjq123/gijela.git

---

## 1. 目标

建立一套可重复、可审计、可回滚的发布策略，确保：

1. 本地开发流程稳定（分支、提交、校验）
2. GitHub 与 Gitee 双远端保持同步
3. 每次发布有版本号、有变更记录、有回滚点

---

## 2. 一次性初始化（仓库级）

在仓库根目录执行以下命令：

```powershell
git remote -v

git remote rename origin github
git remote add gitee https://gitee.com/zhangjq123/gijela.git

git remote set-url github https://github.com/wojiaozhangtudou/gijela.git

git remote -v
```

建议统一主分支为 `main`：

```powershell
git branch -M main
```

首次推送：

```powershell
git push -u github main
git push -u gitee main
```

---

## 3. 分支策略

### 3.1 分支命名

- 主分支：`main`（可发布）
- 功能分支：`feat/<topic>`
- 修复分支：`fix/<topic>`
- 发布分支（可选）：`release/vX.Y.Z`
- 热修复分支：`hotfix/vX.Y.Z-<topic>`

### 3.2 合并规则

- 所有开发先到 `feat/*`、`fix/*`
- 通过自测后合并回 `main`
- `main` 仅保留“可发布状态”代码

---

## 4. 提交规范

推荐使用 Conventional Commits：

- `feat:` 新功能
- `fix:` 缺陷修复
- `docs:` 文档变更
- `refactor:` 重构（不改行为）
- `test:` 测试相关
- `chore:` 构建/脚手架/流程

示例：

```text
feat(chat-flow): add workflow run history filter
fix(pistil): correct role permission refresh logic
docs(readme): update dual-remote publishing guide
```

---

## 5. 发布前检查清单（必须）

### 5.1 代码与文档

- [ ] README 与关键文档已同步
- [ ] 变更影响的接口/配置/权限文档已更新
- [ ] 未提交敏感信息（token、密码、私钥）

### 5.2 后端构建检查

```powershell
mvn -f gijela-core/pom.xml -DskipTests clean install
```

### 5.3 前端构建检查（按需）

```powershell
Set-Location .\gijela-bloom\gijela-bloom-pistil
pnpm install
pnpm build

Set-Location ..\gijela-bloom-chat
pnpm install
pnpm build

Set-Location ..\gijela-bloom-chat-flow
pnpm install
pnpm build
```

> 如仅改动某一前端模块，可只构建对应模块。

---

## 6. 标准发布流程（建议）

### Step 1：更新版本与变更说明

- 在仓库根目录维护 `CHANGELOG.md`（建议新增）
- 约定语义化版本：`vMAJOR.MINOR.PATCH`

### Step 2：合并到 main

```powershell
git checkout main
git pull github main
git merge --no-ff feat/<topic>
```

### Step 3：打 tag

```powershell
git tag -a v1.0.0 -m "release: v1.0.0"
```

### Step 4：双远端推送

```powershell
git push github main --tags
git push gitee main --tags
```

---

## 7. 日常同步策略（防止两边分叉）

每次开发完成后固定执行：

```powershell
git checkout main
git pull github main

git push github main
git push gitee main
```

如果发生冲突，统一以本地合并完成后再双推送，不要在两端平台分别改代码造成“交叉分叉”。

---

## 8. 回滚策略

### 8.1 代码回滚（保留历史）

```powershell
git revert <bad_commit_sha>
git push github main
git push gitee main
```

### 8.2 版本回滚（按 tag）

```powershell
git checkout v1.0.0
# 评估后创建 hotfix 分支进行修复
```

> 不建议直接强推覆盖历史（`push --force`），除非团队明确批准。

---

## 9. .gitignore 与安全建议

建议忽略以下内容（若未配置请补齐）：

- `**/target/`
- `**/logs/`
- `node_modules/`
- `.env*`
- IDE 临时文件

并执行：

```powershell
git status
```

确保没有数据库密码、AccessKey、私钥等敏感文件进入暂存区。

---

## 10. 推荐的最小发布节奏

- 日常：功能完成即小步合并（`feat/*` -> `main`）
- 每周：至少一次稳定 tag（如 `v0.x.y`）
- 重大里程碑：发布说明 + 双平台 Release（GitHub/Gitee 同步）

---

## 11. 双平台发布模板（可复制）

### 11.1 Release 标题

```text
vX.Y.Z - <一句话主题>
```

### 11.2 Release 内容

```text
## 本次发布
- 新增：
- 优化：
- 修复：

## 影响模块
- gijela-core-llm:
- gijela-core-chat:
- gijela-core-chat-flow:
- gijela-core-pistil:

## 升级说明
- 配置变更：
- 数据库变更：
- 兼容性说明：
```

---

## 12. 你当前仓库可直接使用的命令（速查）

```powershell
# 查看远端
git remote -v

# 推送主分支到两个远端
git push github main
git push gitee main

# 发布一个新版本 tag
git tag -a v1.0.0 -m "release: v1.0.0"
git push github v1.0.0
git push gitee v1.0.0
```

---

## 13. 结论

该策略核心是：

1. 统一 `main` 为唯一发布主线
2. 双远端固定同步（GitHub + Gitee）
3. 每次发布必须有 tag 与发布说明
4. 出问题优先 `revert`，不破坏历史

这样可以在保证开发效率的同时，确保双平台仓库长期一致且可追溯。
