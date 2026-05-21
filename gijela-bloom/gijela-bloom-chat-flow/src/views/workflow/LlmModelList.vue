<template>
  <el-card>
    <el-row :gutter="12" style="margin-bottom: 16px" align="middle">
      <el-col :span="8">
        <el-input v-model="queryName" placeholder="按模型键/名称/目标模型搜索" clearable @keyup.enter="load" />
      </el-col>
      <el-col :span="6">
        <el-select v-model="queryEnabled" placeholder="启用状态" clearable style="width: 100%">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-button type="primary" @click="load">查询</el-button>
      </el-col>
      <el-col :span="6" style="text-align: right">
        <el-button @click="goWorkflows" style="margin-right: 8px">返回流程</el-button>
        <el-button type="primary" @click="onCreate">新建模型</el-button>
      </el-col>
    </el-row>

    <el-table :data="rows" v-loading="loading" style="width: 100%">
      <el-table-column prop="modelKey" label="模型键" width="180" />
      <el-table-column prop="displayName" label="展示名称" width="180" />
      <el-table-column prop="provider" label="供应商" width="120" />
      <el-table-column prop="baseUrl" label="模型URL" min-width="240" />
      <el-table-column prop="targetModel" label="目标模型" min-width="180" />
      <el-table-column label="启用" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defaultTemperature" label="默认温度" width="100" />
      <el-table-column prop="defaultMaxTokens" label="默认MaxTokens" width="130" />
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="onEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑模型' : '新建模型'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="模型键" prop="modelKey">
          <el-input v-model="form.modelKey" placeholder="如 qwen-plus-online" />
        </el-form-item>
        <el-form-item label="展示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="如 通义千问 Plus" />
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-input v-model="form.provider" disabled />
        </el-form-item>
        <el-form-item label="模型URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="如 https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入 OpenAI API Key" />
        </el-form-item>
        <el-form-item label="目标模型" prop="targetModel">
          <el-input v-model="form.targetModel" placeholder="如 qwen-plus" />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="enabledSwitch" />
        </el-form-item>
        <el-form-item label="默认温度">
          <el-input-number v-model="form.defaultTemperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="默认MaxTokens">
          <el-input-number v-model="form.defaultMaxTokens" :min="1" :step="100" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" rows="3" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createLlmModel, deleteLlmModel, listLlmModels, updateLlmModel } from '../../api/workflow'
import type { LlmModelItem, LlmModelSavePayload } from '../../types/workflow'

const router = useRouter()
const loading = ref(false)
const rows = ref<LlmModelItem[]>([])
const queryName = ref('')
const queryEnabled = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<(LlmModelSavePayload & { id?: number })>({
  id: undefined,
  modelKey: '',
  displayName: '',
  provider: 'openai',
  baseUrl: '',
  apiKey: '',
  targetModel: '',
  enabled: 1,
  defaultTemperature: 0.7,
  defaultMaxTokens: 2000,
  remark: ''
})

const enabledSwitch = computed({
  get: () => form.enabled === 1,
  set: (v: boolean) => {
    form.enabled = v ? 1 : 0
  }
})

const rules: FormRules = {
  modelKey: [{ required: true, message: '请输入模型键', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入展示名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入模型URL', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  targetModel: [{ required: true, message: '请输入目标模型', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = await listLlmModels({
      name: queryName.value || undefined,
      enabled: queryEnabled.value
    })
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = undefined
  form.modelKey = ''
  form.displayName = ''
  form.provider = 'openai'
  form.baseUrl = ''
  form.apiKey = ''
  form.targetModel = ''
  form.enabled = 1
  form.defaultTemperature = 0.7
  form.defaultMaxTokens = 2000
  form.remark = ''
}

function onCreate() {
  resetForm()
  dialogVisible.value = true
}

function onEdit(row: LlmModelItem) {
  form.id = row.id
  form.modelKey = row.modelKey
  form.displayName = row.displayName
  form.provider = row.provider
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.targetModel = row.targetModel
  form.enabled = row.enabled
  form.defaultTemperature = row.defaultTemperature
  form.defaultMaxTokens = row.defaultMaxTokens
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function onDelete(row: LlmModelItem) {
  await ElMessageBox.confirm(`确认删除模型【${row.displayName}】吗？`, '删除确认', { type: 'warning' })
  await deleteLlmModel(row.id)
  ElMessage.success('删除成功')
  await load()
}

async function onSubmit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return

  const payload: LlmModelSavePayload = {
    modelKey: form.modelKey,
    displayName: form.displayName,
    provider: form.provider,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey,
    targetModel: form.targetModel,
    enabled: form.enabled,
    defaultTemperature: form.defaultTemperature,
    defaultMaxTokens: form.defaultMaxTokens,
    remark: form.remark
  }

  if (form.id) {
    await updateLlmModel(form.id, payload)
  } else {
    await createLlmModel(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

function goWorkflows() {
  router.push('/workflows')
}

onMounted(load)
</script>
