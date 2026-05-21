<template>
  <div class="alerts-page">
    <el-row :gutter="16" class="summary-row">
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover">
          <div class="summary-item">
            <span class="summary-label">规则总数</span>
            <strong>{{ rules.length }}</strong>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover">
          <div class="summary-item">
            <span class="summary-label">启用规则</span>
            <strong>{{ enabledRuleCount }}</strong>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover">
          <div class="summary-item">
            <span class="summary-label">未处理事件</span>
            <strong>{{ openEventCount }}</strong>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <span>告警规则</span>
          <div class="panel-actions">
            <el-button @click="loadRules">刷新</el-button>
            <el-button type="primary" @click="openCreateDialog">新建规则</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rules" border stripe v-loading="rulesLoading">
        <el-table-column prop="name" label="规则名称" min-width="180" />
        <el-table-column prop="metricType" label="指标" width="140" />
        <el-table-column label="阈值" width="160">
          <template #default="{ row }">
            {{ row.condition }} {{ row.threshold }}
          </template>
        </el-table-column>
        <el-table-column prop="windowMinutes" label="窗口" width="100">
          <template #default="{ row }">{{ row.windowMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="级别" width="110">
          <template #default="{ row }">
            <el-tag :type="severityTagType(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通知渠道" min-width="150">
          <template #default="{ row }">{{ row.notifyChannels.join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column prop="recentEventCount" label="24h 触发" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="(value: string | number | boolean) => handleToggleRule(row, Boolean(value))"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="warning" @click="handleTriggerRule(row)">立即评估</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <span>告警事件</span>
          <div class="panel-actions filters">
            <el-select v-model="eventQuery.status" clearable placeholder="状态" style="width: 120px">
              <el-option label="未处理" value="open" />
              <el-option label="已确认" value="ack" />
              <el-option label="已关闭" value="resolved" />
            </el-select>
            <el-select v-model="eventQuery.ruleId" clearable placeholder="规则" style="width: 200px">
              <el-option v-for="rule in rules" :key="rule.id" :label="rule.name" :value="rule.id" />
            </el-select>
            <el-button type="primary" @click="loadEvents">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="events" border stripe v-loading="eventsLoading">
        <el-table-column prop="triggeredAt" label="触发时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则" min-width="160" />
        <el-table-column prop="metricValue" label="指标值" width="110" />
        <el-table-column label="级别" width="110">
          <template #default="{ row }">
            <el-tag :type="severityTagType(row.alertLevel)">{{ row.alertLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notificationCount" label="通知数" width="90" />
        <el-table-column prop="message" label="内容" min-width="320" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'open'" @click="handleAckEvent(row)">
              确认
            </el-button>
            <el-button link type="success" :disabled="row.status === 'resolved'" @click="handleResolveEvent(row)">
              关闭
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="eventQuery.pageNo"
          :page-size="eventQuery.pageSize"
          :total="eventTotal"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建告警规则' : '编辑告警规则'" width="720px">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则名称">
              <el-input v-model="form.name" placeholder="请输入规则名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="告警级别">
              <el-select v-model="form.severity">
                <el-option label="info" value="info" />
                <el-option label="warning" value="warning" />
                <el-option label="critical" value="critical" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="指标类型">
              <el-select v-model="form.metricType">
                <el-option label="QPS" value="qps" />
                <el-option label="错误率" value="errorRate" />
                <el-option label="P95 延迟" value="p95Latency" />
                <el-option label="首 Token 延迟" value="firstTokenMs" />
                <el-option label="工具错误率" value="toolErrorRate" />
                <el-option label="消费 Token 数" value="totalTokens" />
                <el-option label="ES 写入失败" value="esIngestFailure" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="比较条件">
              <el-select v-model="form.condition">
                <el-option label=">" value="gt" />
                <el-option label=">=" value="gte" />
                <el-option label="=" value="eq" />
                <el-option label="<" value="lt" />
                <el-option label="<=" value="lte" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="阈值">
              <el-input-number v-model="form.threshold" :min="0" :precision="2" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="窗口(分钟)">
              <el-input-number v-model="form.windowMinutes" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="冷却期(分钟)">
              <el-input-number v-model="form.cooldownMinutes" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="规则描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选，说明规则用途与处置方式" />
        </el-form-item>

        <el-form-item label="通知渠道">
          <el-checkbox-group v-model="form.notifyChannels">
            <el-checkbox label="wechat">企业微信</el-checkbox>
            <el-checkbox label="dingding">钉钉</el-checkbox>
            <el-checkbox label="email">邮件</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="微信接收人">
              <el-input v-model="form.wechatRecipients" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="钉钉接收人">
              <el-input v-model="form.dingdingRecipients" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="邮件接收人">
              <el-input v-model="form.emailRecipients" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveRule">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ackAlertEvent,
  createAlertRule,
  getMockAlertEvents,
  getMockAlertRules,
  listAlertRules,
  pageAlertEvents,
  resolveAlertEvent,
  toggleAlertRule,
  triggerAlertRule,
  updateAlertRule,
  type AlertEvent,
  type AlertRule,
  type AlertRulePayload
} from '@/api/llmLog'

type DialogMode = 'create' | 'edit'

interface RuleFormState {
  id?: number
  name: string
  description: string
  metricType: string
  condition: string
  threshold: number
  windowMinutes: number
  severity: 'info' | 'warning' | 'critical'
  cooldownMinutes: number
  notifyChannels: string[]
  wechatRecipients: string
  dingdingRecipients: string
  emailRecipients: string
}

const rules = ref<AlertRule[]>([])
const events = ref<AlertEvent[]>([])
const rulesLoading = ref(false)
const eventsLoading = ref(false)
const eventTotal = ref(0)
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')

const eventQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  status: '',
  ruleId: undefined as number | undefined
})

const form = reactive<RuleFormState>(createEmptyForm())

const enabledRuleCount = computed(() => rules.value.filter(item => item.enabled === 1).length)
const openEventCount = computed(() => events.value.filter(item => item.status === 'open').length)

onMounted(async () => {
  await Promise.all([loadRules(), loadEvents()])
})

async function loadRules() {
  rulesLoading.value = true
  try {
    const data = await listAlertRules()
    rules.value = Array.isArray(data) ? data : []
  } catch {
    rules.value = getMockAlertRules()
    ElMessage.warning('告警规则接口未就绪，已切换为 Mock 数据展示')
  } finally {
    rulesLoading.value = false
  }
}

async function loadEvents() {
  eventsLoading.value = true
  try {
    const data = await pageAlertEvents({
      pageNo: eventQuery.pageNo,
      pageSize: eventQuery.pageSize,
      status: eventQuery.status || undefined,
      ruleId: eventQuery.ruleId
    })
    events.value = data?.content ?? []
    eventTotal.value = data?.totalElements ?? 0
  } catch {
    const mock = getMockAlertEvents(eventQuery.pageNo, eventQuery.pageSize)
    let content = mock.content
    if (eventQuery.status) {
      content = content.filter(item => item.status === eventQuery.status)
    }
    if (eventQuery.ruleId) {
      content = content.filter(item => item.ruleId === eventQuery.ruleId)
    }
    events.value = content
    eventTotal.value = mock.totalElements
  } finally {
    eventsLoading.value = false
  }
}

function openCreateDialog() {
  dialogMode.value = 'create'
  Object.assign(form, createEmptyForm())
  dialogVisible.value = true
}

function openEditDialog(rule: AlertRule) {
  dialogMode.value = 'edit'
  Object.assign(form, createEmptyForm(), {
    id: rule.id,
    name: rule.name,
    description: rule.description ?? '',
    metricType: rule.metricType,
    condition: rule.condition,
    threshold: rule.threshold,
    windowMinutes: rule.windowMinutes,
    severity: rule.severity,
    cooldownMinutes: 5,
    notifyChannels: [...rule.notifyChannels],
    wechatRecipients: (rule.notifyRecipients?.wechat ?? []).join(','),
    dingdingRecipients: (rule.notifyRecipients?.dingding ?? []).join(','),
    emailRecipients: (rule.notifyRecipients?.email ?? []).join(',')
  })
  dialogVisible.value = true
}

async function handleSaveRule() {
  if (!form.name.trim()) {
    ElMessage.warning('请先填写规则名称')
    return
  }
  if (!form.notifyChannels.length) {
    ElMessage.warning('请至少选择一个通知渠道')
    return
  }

  const payload: AlertRulePayload = {
    name: form.name.trim(),
    description: form.description.trim(),
    enabled: 1,
    metricType: form.metricType,
    condition: form.condition,
    threshold: form.threshold,
    windowMinutes: form.windowMinutes,
    severity: form.severity,
    cooldownMinutes: form.cooldownMinutes,
    notifyChannels: [...form.notifyChannels],
    notifyRecipients: buildNotifyRecipients()
  }

  try {
    if (dialogMode.value === 'create') {
      await createAlertRule(payload)
      ElMessage.success('告警规则已创建')
    } else if (form.id) {
      await updateAlertRule(form.id, payload)
      ElMessage.success('告警规则已更新')
    }
    dialogVisible.value = false
    await loadRules()
  } catch {
    upsertMockRule(payload)
    dialogVisible.value = false
    ElMessage.warning('后端规则接口未就绪，已先更新页面侧 Mock 状态')
  }
}

async function handleToggleRule(rule: AlertRule, enabled: boolean) {
  try {
    await toggleAlertRule(rule.id, enabled)
    rule.enabled = enabled ? 1 : 0
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  } catch {
    rule.enabled = enabled ? 1 : 0
    ElMessage.warning('切换结果仅已更新到本地展示')
  }
}

async function handleTriggerRule(rule: AlertRule) {
  try {
    await triggerAlertRule(rule.id)
    ElMessage.success('已发起规则评估')
  } catch {
    events.value = [
      {
        id: Date.now(),
        ruleId: rule.id,
        ruleName: rule.name,
        triggeredAt: new Date().toISOString(),
        metricValue: Number(rule.threshold) + 1,
        message: `[${rule.severity.toUpperCase()}] ${rule.name} 触发告警：${rule.metricType} ${rule.condition} ${Number(rule.threshold) + 1}`,
        status: 'open',
        alertLevel: rule.severity,
        notificationCount: rule.notifyChannels.length
      },
      ...events.value
    ]
    eventTotal.value += 1
    ElMessage.warning('手动评估已以 Mock 方式加入事件列表')
  }
}

async function handleAckEvent(event: AlertEvent) {
  try {
    await ackAlertEvent(event.id)
    ElMessage.success('告警已确认')
  } catch {
    event.status = 'ack'
    event.ackBy = 'system'
    event.ackAt = new Date().toISOString()
    ElMessage.warning('后端未就绪，已先更新本地状态')
  } finally {
    await loadEvents()
  }
}

async function handleResolveEvent(event: AlertEvent) {
  try {
    await resolveAlertEvent(event.id)
    ElMessage.success('告警已关闭')
  } catch {
    event.status = 'resolved'
    event.resolvedBy = 'system'
    event.resolvedAt = new Date().toISOString()
    ElMessage.warning('后端未就绪，已先更新本地状态')
  } finally {
    await loadEvents()
  }
}

async function handlePageChange(page: number) {
  eventQuery.pageNo = page
  await loadEvents()
}

function upsertMockRule(payload: AlertRulePayload) {
  const recipients = payload.notifyRecipients
  if (dialogMode.value === 'create') {
    rules.value = [
      {
        id: Date.now(),
        name: payload.name,
        description: payload.description,
        metricType: payload.metricType,
        condition: payload.condition,
        threshold: payload.threshold,
        windowMinutes: payload.windowMinutes,
        severity: payload.severity,
        enabled: payload.enabled ?? 1,
        notifyChannels: payload.notifyChannels,
        notifyRecipients: recipients,
        recentEventCount: 0
      },
      ...rules.value
    ]
    return
  }
  rules.value = rules.value.map(item => item.id === form.id
    ? {
        ...item,
        ...payload,
        notifyRecipients: recipients
      }
    : item)
}

function buildNotifyRecipients() {
  return {
    wechat: splitRecipients(form.wechatRecipients),
    dingding: splitRecipients(form.dingdingRecipients),
    email: splitRecipients(form.emailRecipients)
  }
}

function splitRecipients(value: string) {
  return value.split(',').map(item => item.trim()).filter(Boolean)
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

function severityTagType(level: string) {
  if (level === 'critical') return 'danger'
  if (level === 'warning') return 'warning'
  return 'info'
}

function statusTagType(status: string) {
  if (status === 'open') return 'danger'
  if (status === 'ack') return 'warning'
  return 'success'
}

function createEmptyForm(): RuleFormState {
  return {
    name: '',
    description: '',
    metricType: 'errorRate',
    condition: 'gt',
    threshold: 5,
    windowMinutes: 5,
    severity: 'warning',
    cooldownMinutes: 5,
    notifyChannels: ['wechat'],
    wechatRecipients: '',
    dingdingRecipients: '',
    emailRecipients: ''
  }
}
</script>

<style scoped>
.alerts-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-row {
  margin-bottom: 0;
}

.summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
}

.summary-label {
  color: #909399;
  font-size: 14px;
}

.panel-card :deep(.el-card__header) {
  padding: 16px 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filters {
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .panel-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
