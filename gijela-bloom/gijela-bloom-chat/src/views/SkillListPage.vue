<template>
  <div class="skill-page">
    <div class="page-header">
      <div>
        <h1>技能管理</h1>
        <p>查看 Chat 模块当前已加载的技能，可启停或手动热重载本地技能目录。</p>
      </div>
      <el-space>
        <el-button type="primary" plain :loading="reloading" @click="onReload">
          <el-icon><RefreshRight /></el-icon>
          <span style="margin-left:4px">热重载</span>
        </el-button>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <el-card shadow="never" class="skill-card">
      <template #header>
        <div class="panel-header">已加载技能列表（共 {{ list.length }} 个）</div>
      </template>
      <el-table :data="list" v-loading="loading" stripe style="width:100%" row-key="name">
        <el-table-column label="名称" prop="name" min-width="180">
          <template #default="{ row }">
            <a class="link" @click="openDetail(row.name)">{{ row.name }}</a>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag :type="row.source === 'BUILTIN' ? 'info' : 'success'" effect="light">{{ row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" prop="version" width="100" />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="row.toggling"
              @change="(v: boolean) => onToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="上次加载时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.lastLoadedAt) }}</template>
        </el-table-column>
        <el-table-column label="最近错误" min-width="200">
          <template #default="{ row }">
            <span v-if="row.errorMsg" class="err">{{ row.errorMsg }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.name)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      :title="detail ? `技能详情 - ${detail.name}` : '技能详情'"
      width="720px"
      top="8vh"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="来源">
              <el-tag :type="detail.source === 'BUILTIN' ? 'info' : 'success'" effect="light">{{ detail.source }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detail.enabled ? 'success' : 'danger'" effect="light">
                {{ detail.enabled ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="是否内置">{{ detail.builtin ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="上次加载时间">{{ formatTime(detail.lastLoadedAt) }}</el-descriptions-item>
            <el-descriptions-item label="入口" :span="2"><code>{{ detail.entry }}</code></el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.sourcePath" label="清单路径" :span="2">
              <code>{{ detail.sourcePath }}</code>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.errorMsg" label="最近错误" :span="2">
              <span class="err">{{ detail.errorMsg }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="block-title">入参 Schema</div>
          <pre class="code-block">{{ schemaText }}</pre>

          <template v-if="detail.manifestRaw">
            <div class="block-title">SKILL.md</div>
            <pre class="code-block">{{ detail.manifestRaw }}</pre>
          </template>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import { getSkillDetail, listSkills, reloadSkills, toggleSkill } from '@/api/skill'
import type { SkillDetail, SkillSummary } from '@/types/skill'

interface Row extends SkillSummary {
  toggling?: boolean
}

const router = useRouter()
const list = ref<Row[]>([])
const loading = ref(false)
const reloading = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SkillDetail | null>(null)

const schemaText = computed(() => {
  try { return JSON.stringify(detail.value?.inputSchema || {}, null, 2) } catch { return '' }
})

function formatTime(s: string | null) {
  if (!s) return '-'
  return s.replace('T', ' ').replace(/\.[0-9]+/, '')
}

async function load() {
  loading.value = true
  try {
    const data = await listSkills()
    list.value = data.map(d => ({ ...d, toggling: false }))
  } catch (e: any) {
    ElMessage.error('加载技能列表失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

async function onToggle(row: Row, next: boolean) {
  row.toggling = true
  try {
    const v = await toggleSkill(row.name, next)
    row.enabled = v
    ElMessage.success(v ? '已启用' : '已禁用')
  } catch (e: any) {
    ElMessage.error('启停失败：' + (e?.message || e))
  } finally {
    row.toggling = false
  }
}

async function onReload() {
  reloading.value = true
  try {
    await reloadSkills()
    ElMessage.success('热重载完成')
    await load()
  } catch (e: any) {
    ElMessage.error('热重载失败：' + (e?.message || e))
  } finally {
    reloading.value = false
  }
}

async function openDetail(name: string) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getSkillDetail(name)
  } catch (e: any) {
    ElMessage.error('加载技能详情失败：' + (e?.message || e))
  } finally {
    detailLoading.value = false
  }
}

function goChatPage() {
  void router.push('/')
}

onMounted(load)
</script>

<style scoped>
.skill-page {
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

.page-header h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.page-header p {
  margin: 0;
  color: #606266;
}

.skill-card {
  border-radius: 16px;
  flex: 1;
  min-height: 0;
}

.panel-header {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.link {
  color: var(--el-color-primary);
  cursor: pointer;
}
.link:hover { text-decoration: underline; }

.err {
  color: var(--el-color-danger);
  word-break: break-all;
}
.muted { color: #909399; }

code {
  font-family: Menlo, Consolas, monospace;
  background: #f0f2f5;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.detail-body { max-height: 65vh; overflow: auto; }

.block-title {
  margin: 16px 0 8px;
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
</style>
