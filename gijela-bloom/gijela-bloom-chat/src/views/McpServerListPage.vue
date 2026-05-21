<template>
  <div class="mcp-page">
    <div class="page-header">
      <div>
        <h1>MCP 管理</h1>
        <p>管理外部 MCP（Model Context Protocol）Server，支持 Streamable HTTP / SSE / stdio 三种 transport，提供新增/编辑/删除/启停/连通性测试。</p>
      </div>
      <el-space>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon><span style="margin-left:4px">新增 MCP Server</span>
        </el-button>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <el-card shadow="never" class="mcp-card">
      <template #header>
        <div class="panel-header">已注册 MCP Server（共 {{ list.length }} 个）</div>
      </template>
      <el-table :data="list" v-loading="loading" stripe row-key="id" style="width:100%">
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <a class="link" @click="openDetail(row.name)">{{ row.name }}</a>
            <div class="muted small">{{ row.displayName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Transport" width="140">
          <template #default="{ row }">
            <el-tag effect="light" :type="transportTagType(row.transport)">{{ transportLabel(row.transport) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接目标" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <code v-if="row.transport === 'stdio'" class="endpoint-code">$ {{ row.command || row.endpointOrCommand }}</code>
            <code v-else class="endpoint-code">{{ row.endpoint || row.endpointOrCommand }}</code>
          </template>
        </el-table-column>
        <el-table-column label="鉴权" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.transport === 'stdio'" type="info" effect="plain">N/A</el-tag>
            <template v-else>
              <el-tag v-if="row.authType === 'bearer'" type="warning" effect="light">Bearer</el-tag>
              <el-tag v-else type="info" effect="light">无</el-tag>
              <span v-if="row.authType === 'bearer' && row.hasToken" class="muted small" style="margin-left:4px">已设</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工具数" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ muted: row.toolCount === null }">{{ row.toolCount ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="row.toggling"
              @change="(v: boolean) => onToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最近测试" min-width="160">
          <template #default="{ row }">
            <div>{{ formatTime(row.lastTestedAt) }}</div>
            <div v-if="row.statusMessage" class="muted small" :title="row.statusMessage">
              {{ truncate(row.statusMessage, 32) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onTest(row)" :loading="row.testing">测试</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openDetail(row.name)">详情</el-button>
            <el-popconfirm
              :title="`确认删除 MCP Server「${row.name}」？`"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="onDelete(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? `编辑 MCP Server - ${form.name}` : '新增 MCP Server'"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        @submit.prevent
      >
        <el-form-item label="唯一标识" prop="name">
          <el-input
            v-model="form.name"
            placeholder="3-64 位小写字母/数字/短横线，如 my-mcp-server"
            :disabled="editing"
          />
        </el-form-item>
        <el-form-item label="展示名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="便于识别的中文/英文名" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="可选；这台 MCP Server 的用途/能力简介"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="Transport" prop="transport">
          <el-radio-group v-model="form.transport">
            <el-radio value="streamable_http">Streamable HTTP（推荐）</el-radio>
            <el-radio value="sse">SSE</el-radio>
            <el-radio value="stdio">stdio</el-radio>
          </el-radio-group>
          <div v-if="form.transport === 'stdio'" class="muted small warn">
            ⚠ stdio 会在管理端宿主机上 fork 子进程；请确保命令可信。后端需配置 chat.mcp.allow-stdio=true 才能创建。
          </div>
        </el-form-item>

        <!-- streamable_http / sse 共用 -->
        <template v-if="form.transport !== 'stdio'">
          <el-form-item label="Endpoint" prop="endpoint">
            <el-input v-model="form.endpoint" placeholder="https://example.com/mcp 或 http://localhost:8080/mcp" />
            <div class="muted small">
              {{ form.transport === 'sse' ? '指向 SSE 长连接入口（GET /sse）' : '指向 Streamable HTTP 单端点（POST）' }}；仅允许 https 或 http://localhost
            </div>
          </el-form-item>
          <el-form-item label="鉴权方式" prop="authType">
            <el-radio-group v-model="form.authType">
              <el-radio value="none">无鉴权</el-radio>
              <el-radio value="bearer">Bearer Token</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.authType === 'bearer'" :label="editing ? '新 Token（留空不变）' : 'Token'">
            <el-input
              v-model="tokenInput"
              type="password"
              show-password
              :placeholder="editing ? '留空表示保持原有 token' : '请输入 Bearer Token'"
              maxlength="1024"
            />
            <div v-if="editing" class="muted small">
              当前：{{ form.authTokenMask || '（未设置）' }}；如需清空请输入空格再清空保存
            </div>
          </el-form-item>
        </template>

        <!-- stdio 专属 -->
        <template v-if="form.transport === 'stdio'">
          <el-form-item label="Command" prop="command">
            <el-input v-model="form.command" placeholder="如 python / node / uvx" maxlength="256" />
          </el-form-item>
          <el-form-item label="Args">
            <el-select
              v-model="form.args"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="按回车添加，如 -m / mcp-server-xxx"
              style="width:100%"
            />
          </el-form-item>
          <el-form-item label="环境变量">
            <el-input
              v-model="envText"
              type="textarea"
              :rows="3"
              placeholder="每行 KEY=VALUE，例如：\nMY_TOKEN=xxx\nDEBUG=1"
            />
            <div class="muted small">解析失败的行会被丢弃；保存时再次校验。</div>
          </el-form-item>
          <el-form-item label="工作目录">
            <el-input v-model="form.workingDir" placeholder="可选，绝对路径" maxlength="512" />
          </el-form-item>
        </template>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ editing ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="detail ? `MCP Server 详情 - ${detail.name}` : 'MCP Server 详情'"
      width="760px"
      top="6vh"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="展示名">{{ detail.displayName }}</el-descriptions-item>
            <el-descriptions-item label="Transport">
              <el-tag effect="light" :type="transportTagType(detail.transport)">{{ transportLabel(detail.transport) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="启用">
              <el-tag :type="detail.enabled ? 'success' : 'danger'" effect="light">
                {{ detail.enabled ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detail.status)" effect="light">{{ statusLabel(detail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="工具数">{{ detail.tools?.length ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="鉴权">
              <span v-if="detail.transport === 'stdio'" class="muted">N/A</span>
              <template v-else>
                <span>{{ detail.authType === 'bearer' ? 'Bearer' : '无' }}</span>
                <code v-if="detail.authType === 'bearer' && detail.authTokenMask" style="margin-left:8px">{{ detail.authTokenMask }}</code>
              </template>
            </el-descriptions-item>
            <el-descriptions-item label="最近测试时间">{{ formatTime(detail.lastTestedAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.transport !== 'stdio'" label="Endpoint" :span="2"><code>{{ detail.endpoint }}</code></el-descriptions-item>
            <template v-else>
              <el-descriptions-item label="Command" :span="2"><code>{{ detail.command }}</code></el-descriptions-item>
              <el-descriptions-item v-if="detail.args?.length" label="Args" :span="2">
                <el-tag v-for="(a, i) in detail.args" :key="i" effect="plain" style="margin-right:4px">{{ a }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="detail.env && Object.keys(detail.env).length" label="环境变量" :span="2">
                <pre class="code-block-mini">{{ envOf(detail.env) }}</pre>
              </el-descriptions-item>
              <el-descriptions-item v-if="detail.workingDir" label="工作目录" :span="2">
                <code>{{ detail.workingDir }}</code>
              </el-descriptions-item>
            </template>
            <el-descriptions-item v-if="detail.description" label="描述" :span="2">
              {{ detail.description }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.statusMessage" label="最近消息" :span="2">
              <span :class="detail.status === 'error' ? 'err' : ''">{{ detail.statusMessage }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="block-title">
            <span>已发现工具（tools/list）</span>
            <el-button size="small" :loading="refreshingTools" @click="onRefreshTools">重新拉取</el-button>
          </div>

          <el-empty v-if="!detail.tools?.length" description="尚未拉取工具，点击右上角“重新拉取”" :image-size="80" />
          <el-collapse v-else>
            <el-collapse-item v-for="t in detail.tools" :key="String(t.name)" :name="String(t.name)">
              <template #title>
                <strong>{{ t.name }}</strong>
                <span v-if="t.description" class="muted" style="margin-left:8px">{{ t.description }}</span>
              </template>
              <pre class="code-block">{{ jsonOf(t.inputSchema || t) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createMcpServer,
  deleteMcpServer,
  getMcpServerDetail,
  listMcpServers,
  refreshMcpServerTools,
  testMcpServer,
  toggleMcpServer,
  updateMcpServer
} from '@/api/mcp'
import type {
  McpAuthType,
  McpServerDetail,
  McpServerSaveRequest,
  McpServerSummary,
  McpStatus,
  McpTransport
} from '@/types/mcp'

interface Row extends McpServerSummary {
  toggling?: boolean
  testing?: boolean
}

const router = useRouter()
const list = ref<Row[]>([])
const loading = ref(false)

const formVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)
const tokenInput = ref('')
const envText = ref('')
const form = reactive<McpServerSaveRequest & { authTokenMask?: string | null }>({
  name: '',
  displayName: '',
  description: '',
  transport: 'streamable_http' as McpTransport,
  endpoint: '',
  command: '',
  args: [],
  env: {},
  workingDir: '',
  authType: 'none' as McpAuthType,
  authToken: null,
  enabled: true,
  authTokenMask: null
})

const rules = computed<FormRules>(() => ({
  name: [
    { required: true, message: '请输入唯一标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$/,
      message: '只能小写字母/数字/短横线，长度 3-64',
      trigger: 'blur'
    }
  ],
  displayName: [{ required: true, message: '请输入展示名', trigger: 'blur' }],
  endpoint: [
    {
      required: true,
      validator: (_r: any, v: string, cb: (e?: Error) => void) => {
        if (form.transport === 'stdio') return cb()
        return v && v.trim() ? cb() : cb(new Error('请输入 Endpoint URL'))
      },
      trigger: 'blur'
    }
  ],
  command: [
    {
      required: true,
      validator: (_r: any, v: string, cb: (e?: Error) => void) => {
        if (form.transport !== 'stdio') return cb()
        return v && v.trim() ? cb() : cb(new Error('请输入启动命令'))
      },
      trigger: 'blur'
    }
  ],
  transport: [{ required: true }],
  authType: [{ required: true }]
}))

const detailVisible = ref(false)
const detailLoading = ref(false)
const refreshingTools = ref(false)
const detail = ref<McpServerDetail | null>(null)

function statusTagType(s: McpStatus) {
  switch (s) {
    case 'ok': return 'success'
    case 'error': return 'danger'
    default: return 'info'
  }
}
function statusLabel(s: McpStatus) {
  switch (s) {
    case 'ok': return '正常'
    case 'error': return '异常'
    default: return '未测试'
  }
}
function transportLabel(t: McpTransport) {
  switch (t) {
    case 'streamable_http': return 'Streamable HTTP'
    case 'sse': return 'SSE'
    case 'stdio': return 'stdio'
    default: return String(t).toUpperCase()
  }
}
function transportTagType(t: McpTransport) {
  switch (t) {
    case 'sse': return 'warning'
    case 'stdio': return 'danger'
    default: return 'primary'
  }
}
function envOf(env: Record<string, string>) {
  return Object.entries(env || {}).map(([k, v]) => `${k}=${v}`).join('\n')
}
function parseEnvText(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const raw of (text || '').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#')) continue
    const idx = line.indexOf('=')
    if (idx <= 0) continue
    const k = line.substring(0, idx).trim()
    const v = line.substring(idx + 1)
    if (k) out[k] = v
  }
  return out
}
function formatTime(s: string | null | undefined) {
  if (!s) return '-'
  return s.replace('T', ' ').replace(/\.[0-9]+/, '')
}
function truncate(s: string, n: number) {
  if (!s) return ''
  return s.length <= n ? s : s.substring(0, n) + '…'
}
function jsonOf(o: any) {
  try { return JSON.stringify(o, null, 2) } catch { return String(o) }
}

async function load() {
  loading.value = true
  try {
    const data = await listMcpServers()
    list.value = data.map(d => ({ ...d, toggling: false, testing: false }))
  } catch (e: any) {
    ElMessage.error('加载 MCP 列表失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.displayName = ''
  form.description = ''
  form.transport = 'streamable_http'
  form.endpoint = ''
  form.command = ''
  form.args = []
  form.env = {}
  form.workingDir = ''
  form.authType = 'none'
  form.authToken = null
  form.enabled = true
  form.authTokenMask = null
  tokenInput.value = ''
  envText.value = ''
}

function openCreate() {
  editing.value = false
  resetForm()
  formVisible.value = true
}

async function openEdit(row: Row) {
  editing.value = true
  resetForm()
  detailLoading.value = true
  try {
    const d = await getMcpServerDetail(row.name)
    if (!d) {
      ElMessage.error('未找到该 MCP Server')
      return
    }
    form.name = d.name
    form.displayName = d.displayName
    form.description = d.description || ''
    form.transport = d.transport
    form.endpoint = d.endpoint || ''
    form.command = d.command || ''
    form.args = Array.isArray(d.args) ? [...d.args] : []
    form.env = d.env ? { ...d.env } : {}
    form.workingDir = d.workingDir || ''
    envText.value = envOf(form.env)
    form.authType = d.authType
    form.enabled = d.enabled
    form.authTokenMask = d.authTokenMask
    formVisible.value = true
  } catch (e: any) {
    ElMessage.error('加载详情失败：' + (e?.message || e))
  } finally {
    detailLoading.value = false
  }
}

async function onSave() {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    saving.value = true
    try {
      const isStdio = form.transport === 'stdio'
      const payload: McpServerSaveRequest = isStdio
        ? {
            name: form.name,
            displayName: form.displayName,
            description: form.description || null,
            transport: form.transport,
            command: form.command || '',
            args: form.args || [],
            env: parseEnvText(envText.value),
            workingDir: form.workingDir || null,
            authType: 'none',
            authToken: null,
            enabled: form.enabled
          }
        : {
            name: form.name,
            displayName: form.displayName,
            description: form.description || null,
            transport: form.transport,
            endpoint: form.endpoint,
            authType: form.authType,
            enabled: form.enabled,
            authToken:
              form.authType === 'none'
                ? null
                : editing.value
                  ? (tokenInput.value === '' ? null : tokenInput.value)
                  : tokenInput.value
          }
      if (editing.value) {
        await updateMcpServer(form.name, payload)
        ElMessage.success('保存成功')
      } else {
        await createMcpServer(payload)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      await load()
    } catch (e: any) {
      ElMessage.error('保存失败：' + (e?.response?.data?.msg || e?.message || e))
    } finally {
      saving.value = false
    }
  })
}

async function onToggle(row: Row, next: boolean) {
  row.toggling = true
  try {
    const d = await toggleMcpServer(row.name, next)
    if (d) row.enabled = d.enabled
    ElMessage.success(next ? '已启用' : '已禁用')
  } catch (e: any) {
    ElMessage.error('启停失败：' + (e?.response?.data?.msg || e?.message || e))
  } finally {
    row.toggling = false
  }
}

async function onTest(row: Row) {
  row.testing = true
  try {
    const r = await testMcpServer(row.name)
    if (r.ok) {
      ElMessage.success(`连接成功：${r.serverInfo?.name || ''}/${r.serverInfo?.version || ''}`)
    } else {
      ElMessage.error('连接失败：' + (r.message || ''))
    }
    await load()
  } catch (e: any) {
    ElMessage.error('测试失败：' + (e?.response?.data?.msg || e?.message || e))
  } finally {
    row.testing = false
  }
}

async function onDelete(row: Row) {
  try {
    await deleteMcpServer(row.name)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.response?.data?.msg || e?.message || e))
  }
}

async function openDetail(name: string) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getMcpServerDetail(name)
  } catch (e: any) {
    ElMessage.error('加载详情失败：' + (e?.message || e))
  } finally {
    detailLoading.value = false
  }
}

async function onRefreshTools() {
  if (!detail.value) return
  refreshingTools.value = true
  try {
    const d = await refreshMcpServerTools(detail.value.name)
    if (d) detail.value = d
    ElMessage.success('已刷新工具列表，共 ' + (d?.tools?.length || 0) + ' 个')
    await load()
  } catch (e: any) {
    ElMessage.error('拉取工具失败：' + (e?.response?.data?.msg || e?.message || e))
  } finally {
    refreshingTools.value = false
  }
}

function goChatPage() { void router.push('/') }

onMounted(load)
</script>

<style scoped>
.mcp-page {
  height: 100dvh;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  overflow: auto;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f5f7fa 0%, #eef3ff 100%);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-header h1 { margin: 0 0 8px; font-size: 28px; }
.page-header p { margin: 0; color: #606266; }

.mcp-card { border-radius: 16px; flex: 1; min-height: 0; }
.panel-header { font-weight: 600; font-size: 14px; color: #303133; }

.link { color: var(--el-color-primary); cursor: pointer; }
.link:hover { text-decoration: underline; }

.muted { color: #909399; }
.small { font-size: 12px; }
.err { color: var(--el-color-danger); word-break: break-all; }

code {
  font-family: Menlo, Consolas, monospace;
  background: #f0f2f5;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.detail-body { max-height: 70vh; overflow: auto; }

.block-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 10px;
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.code-block {
  max-height: 280px;
  overflow: auto;
  background: #f7f9fc;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.code-block-mini {
  max-height: 140px;
  overflow: auto;
  background: #f7f9fc;
  padding: 8px;
  border-radius: 6px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.endpoint-code {
  word-break: break-all;
}

.warn {
  color: var(--el-color-warning);
  margin-top: 4px;
}
</style>
