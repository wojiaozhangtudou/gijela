目的

为本仓库制定明确、可执行的国际化（i18n）标准，保证后续自动生成或人工实现的代码一致、可维护、易翻译。

范围

- 前端 Vue 3 + TypeScript 代码（模板与脚本）
- Element Plus 组件文本与警告/提示
- locales 资源（`src/i18n/locales/*.json`）
- 文档（README/QUICK_START/备忘录）可选处理

核心约定（必须遵守）

1. 翻译 key 命名
- 统一使用小写点号命名空间：`domain.item.subitem`，例如 `message.login`, `user.create`, `validation.required`。
- 常用命名空间：
  - `message.*`：通用界面文本（按钮、标题、提示）
  - `user.*`：用户相关字段与动作
  - `menu.*`：菜单项
  - `validation.*`：表单校验文案
  - `page.<pageName>.*`：单页面/复杂页面专用键
- 不要直接使用中文/英文作为 key；key 必须为英文单词或缩写，便于后端合并与替换。

2. 文本占位与插值
- 使用 vue-i18n 插值语法（`{name}`）：`t('message.welcome', { name })`。
- 占位变量使用短小英文单词，小写：`{count}`, `{name}`, `{date}`。
- 对于复数/复杂语法，使用 vue-i18n 的 pluralization 或 message format（根据需要引入 Intl）。

3. 模板与脚本中使用方式
- 模板：优先使用 `$t('key')`。
  - 示例：`<h3>{{ $t('message.login') }}</h3>`。
- 组合式脚本：`const { t, locale } = useI18n()`，使用 `t('key')` 返回字符串。
- 禁止硬编码中文或英文到模板/脚本中（例如 button label、表头、提示文本必须使用 i18n）。
- 对于后端返回可能为 display text 或 i18n key 的字段（如 menus），渲染时使用 `$t(item.name) || item.name` 回退。

4. Element Plus 本地化
- Element Plus 的 locale 应与 vue-i18n 的 `locale` 保持一致。
- 现有做法是把选中的语言写入 `localStorage.locale` 并在切换后 `location.reload()` 以让 Element Plus 生效。可以保留该简单可靠策略。
- 可选：为不 reload 的体验，实现 Element Plus 运行时 locale 切换（高级，可作为优化）。

5. locales 文件与结构
- 存放路径：`src/i18n/locales/zh.json`, `src/i18n/locales/en.json`。
- 按命名空间组织 JSON：
  {
    "message": { "login": "登录", "logout": "退出" },
    "user": { "id": "ID", "email": "邮箱" }
  }
- 添加新 key 必须在 `zh.json` 与 `en.json` 两处都添加（英文可先填英文草稿）。
- 保持 JSON 格式合法（无重复键、末尾逗号），PR 时自动校验。

6. 新增 key 的工作流（开发者）
- 步骤：
  1. 在代码中使用新 key（尽量先在组件内用 `t('namespace.key')`）。
  2. 在 `zh.json` 和 `en.json` 添加对应条目（中文/英文），确保语义一致。
  3. 运行 JSON 校验（`npm run lint:json` 或使用 `jsonlint`）。
  4. 在 PR 描述中列出新增 keys，便于翻译人员校对。

7. 翻译注释与上下文
- 在 locales JSON 中可添加 `_comment` 或在 PR 中说明上下文，避免翻译歧义。
- 对具有变量或需要格式化的文本，注明变量含义与示例。

8. 自动化与校验
- 每个 PR 必须通过以下检查：
  - 所有修改的 vue/vuex/router 文件中不包含裸字符串中文/英文（可用脚本检测正则）。
  - `zh.json` 与 `en.json` 的语法检查通过。
  - 新增 key 列表在 PR 描述中明确列出。
- 推荐工具：i18n Ally（VSCode）、jsonlint、自定义脚本（后续可添加）。

9. 文档与 README
- README 中的用户可见文本可以保留为中英文两份（`README.md` 与 `README.en.md`），或抽取到 locales（不推荐影响可读性）。

10. 运行时默认语言策略
- 优先级：`localStorage.locale` -> 浏览器语言 `navigator.language` -> 项目默认 `zh`。
- 推荐在 `src/i18n/index.ts` 与 `src/main.ts` 的 `getSavedLocale()` 中实现该逻辑。

11. 国际化相关代码风格
- 关键字必须使用点式命名，避免深层重复。
- 组件内部的 label/rules/messages 必须从 i18n 获取；校验规则的 message 使用 `t('validation.required')`。
- 对于动态组件标题，使用 `const title = t(route.meta.title) || route.meta.title` 的回退策略。

12. PR 检查清单（每次变更都要在 PR 模板里包含）
- [ ] 新增/修改 key 已同时写入 `zh.json` 和 `en.json`。
- [ ] `zh.json` / `en.json` 通过 JSON 语法校验。
- [ ] 页面中无硬编码用户可见文本（spot-check）。
- [ ] 已更新或新增的 key 列表附在 PR 描述中并说明上下文。

13. 翻译质量与术语库
- 建议维护一个小型术语表（例如 `docs/I18N_TERMS.md`），统一“用户”“角色”“权限”等术语英文翻译。

14. 性能与懒加载
- 大型项目建议懒加载 locale（按需 import `import('./locales/en.json')`），保持主包体积小；但要保证首次切换不会卡顿。

示例片段（说明用途）

- 模板：
  <h3>{{ $t('message.login') }}</h3>
- 组合式脚本：
  const { t, locale } = useI18n()
  t('message.login')
- 菜单回退：
  {{ $t(menu.name) || menu.name }}

下一步建议

- 我可以把这份文档放入仓库（我已经创建 `I18N_GUIDELINES.md`），并在仓库根目录添加一个小型术语表 `docs/I18N_TERMS.md`（可选）。
- 若你同意，我会在 CI pipeline 中添加 JSON 校验脚本与 PR 模板内 i18n 检查项（需要你允许我提交这些改动）。

完成情况

- 已在仓库根创建 `I18N_GUIDELINES.md`，包含上面所有内容。
- 如果需要我可以把部分建议（自动检查脚本、PR 模板变更）直接实现并提交。
