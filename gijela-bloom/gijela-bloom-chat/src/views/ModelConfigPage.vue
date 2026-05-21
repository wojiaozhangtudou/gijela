<template>
  <div class="model-config-page">
    <div class="page-header">
      <div>
        <h1>模型配置管理</h1>
        <p>统一维护对话模型与向量模型配置（数据库单源）。</p>
      </div>
      <el-space>
        <el-button type="primary" @click="openCreateDialog">新增配置</el-button>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="filterType" @change="loadConfigs">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="CHAT">对话模型</el-radio-button>
          <el-radio-button label="EMBEDDING">向量模型</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="configType" label="类型" width="120" />
        <el-table-column prop="providerKey" label="接口风格" width="140" />
        <el-table-column prop="model" label="模型" min-width="180" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="260" show-overflow-tooltip />
        <el-table-column prop="apiKey" label="API Key" min-width="220" show-overflow-tooltip />
        <el-table-column label="超时(s)" width="180">
          <template #default="scope">
            {{ scope.row.connectTimeoutSeconds }}/{{ scope.row.readTimeoutSeconds }}/{{ scope.row.callTimeoutSeconds }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'" effect="light">{{ scope.row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button link type="success" :loading="testingId === scope.row.id" @click="testConfig(scope.row)">测试</el-button>
            <el-button link type="danger" @click="removeConfig(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showDialog" :title="editingId ? '编辑模型配置' : '新增模型配置'" width="640px" destroy-on-close>
      <el-form label-width="120px" label-position="left">
        <el-form-item label="配置类型" required>
          <el-select v-model="form.configType" style="width: 100%">
            <el-option label="CHAT" value="CHAT" />
            <el-option label="EMBEDDING" value="EMBEDDING" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口风格" required>
          <el-input v-model="form.providerKey" disabled maxlength="64" placeholder="openai" />
        </el-form-item>
        <el-form-item label="模型名" required>
          <el-input v-model="form.model" maxlength="128" placeholder="如 qwen-plus / text-embedding-3-large" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" maxlength="512" placeholder="https://xxx/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" show-password placeholder="请输入 API Key" />
        </el-form-item>
        <el-form-item label="连接超时(s)">
          <el-input-number v-model="form.connectTimeoutSeconds" :min="1" :max="600" />
        </el-form-item>
        <el-form-item label="读取超时(s)">
          <el-input-number v-model="form.readTimeoutSeconds" :min="1" :max="600" />
        </el-form-item>
        <el-form-item label="调用超时(s)">
          <el-input-number v-model="form.callTimeoutSeconds" :min="1" :max="1200" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createModelConfig,
  deleteModelConfig,
  listModelConfigs,
  testModelConfig,
  updateModelConfig
} from '@/api/chat'
import type { ModelConfigItem, ModelConfigSaveRequest } from '@/types/chat'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const showDialog = ref(false)
const editingId = ref<number | null>(null)
const testingId = ref<number | null>(null)
const filterType = ref<'ALL' | 'CHAT' | 'EMBEDDING'>('ALL')
const rows = ref<ModelConfigItem[]>([])

const form = ref<ModelConfigSaveRequest>({
  configType: 'CHAT',
  providerKey: 'openai',
  model: '',
  baseUrl: '',
  apiKey: '',
  connectTimeoutSeconds: 10,
  readTimeoutSeconds: 60,
  callTimeoutSeconds: 120,
  enabled: true
})

onMounted(() => {
  void loadConfigs()
})

async function loadConfigs() {
  loading.value = true
  try {
    rows.value = await listModelConfigs(filterType.value === 'ALL' ? undefined : filterType.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载模型配置失败')
  } finally {
    loading.value = false
  }
}

function goChatPage() {
  void router.push('/')
}

function resetForm() {
  form.value = {
    configType: 'CHAT',
    providerKey: 'openai',
    model: '',
    baseUrl: '',
    apiKey: '',
    connectTimeoutSeconds: 10,
    readTimeoutSeconds: 60,
    callTimeoutSeconds: 120,
    enabled: true
  }
}

function openCreateDialog() {
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function openEditDialog(row: ModelConfigItem) {
  editingId.value = row.id
  form.value = {
    configType: row.configType,
    providerKey: 'openai',
    model: row.model,
    baseUrl: row.baseUrl,
    apiKey: row.apiKey || '',
    connectTimeoutSeconds: row.connectTimeoutSeconds,
    readTimeoutSeconds: row.readTimeoutSeconds,
    callTimeoutSeconds: row.callTimeoutSeconds,
    enabled: row.enabled
  }
  showDialog.value = true
}

async function submit() {
  const payload: ModelConfigSaveRequest = {
    ...form.value,
    providerKey: 'openai',
    model: form.value.model.trim(),
    baseUrl: form.value.baseUrl.trim(),
    apiKey: form.value.apiKey?.trim() || undefined
  }
  if (!payload.providerKey || !payload.model || !payload.baseUrl) {
    ElMessage.warning('请填写完整的接口风格、模型、Base URL')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateModelConfig(editingId.value, payload)
    } else {
      await createModelConfig(payload)
    }
    ElMessage.success('保存成功')
    showDialog.value = false
    await loadConfigs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeConfig(row: ModelConfigItem) {
  try {
    await ElMessageBox.confirm(`确认删除配置：${row.providerKey} / ${row.model}？`, '提示', {
      type: 'warning'
    })
    await deleteModelConfig(row.id)
    ElMessage.success('删除成功')
    await loadConfigs()
  } catch {
    // 用户取消
  }
}

async function testConfig(row: ModelConfigItem) {
  testingId.value = row.id
  try {
    const result = await testModelConfig(row.id)
    if (result?.ok) {
      const extra = row.configType === 'EMBEDDING'
        ? `维度：${String(result.dimension || '-')}`
        : `响应：${String(result.preview || '-')}`
      ElMessage.success(`测试成功，耗时 ${String(result.latencyMs || 0)}ms，${extra}`)
    } else {
      ElMessage.error(String(result?.message || '测试失败'))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试失败')
  } finally {
    testingId.value = null
  }
}
</script>

<style scoped>
.model-config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
}

.page-header p {
  margin: 6px 0 0;
  color: #6b7280;
}

.toolbar {
  margin-bottom: 12px;
}
</style>
