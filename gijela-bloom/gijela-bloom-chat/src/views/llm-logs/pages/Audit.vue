<template>
  <div class="audit-page">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <span>审计检索</span>
          <div class="panel-actions">
            <el-button @click="resetFilters">重置</el-button>
            <el-button @click="loadRecords">查询</el-button>
            <el-button type="primary" :loading="exporting" @click="handleExport">导出 CSV</el-button>
          </div>
        </div>
      </template>

      <el-form :model="filters" inline>
        <el-form-item label="租户">
          <el-input v-model="filters.tenantId" clearable placeholder="default" style="width: 150px" />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="filters.userId" clearable placeholder="userId" style="width: 160px" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="filters.modelRoute" clearable placeholder="如 gpt-4o" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 130px">
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="错误码">
          <el-input v-model="filters.errorCode" clearable placeholder="如 RATE_LIMIT" style="width: 180px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="提示词/错误消息" style="width: 200px" />
        </el-form-item>
        <el-form-item label="时延区间(ms)">
          <el-input-number v-model="filters.minLatency" :min="0" style="width: 120px" />
          <span class="sep">-</span>
          <el-input-number v-model="filters.maxLatency" :min="0" style="width: 120px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            unlink-panels
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <span>审计日志记录</span>
          <span class="hint">共 {{ total }} 条</span>
        </div>
      </template>

      <el-table :data="rows" border stripe v-loading="loading" empty-text="暂无数据">
        <el-table-column prop="eventTime" label="时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!row.traceId" @click="openTrace(String(row.traceId || ''))">
              {{ row.traceId || '-' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="sessionId" label="Session" min-width="180" show-overflow-tooltip />
        <el-table-column prop="modelRoute" label="模型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="latencyMs" label="延迟(ms)" width="110" />
        <el-table-column prop="totalTokens" label="Tokens" width="100" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="outputText" label="模型输出" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="output-text">{{ row.outputText || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pageNo"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="traceDrawerVisible" title="Trace 审计详情" size="48%">
      <div class="trace-meta">Trace ID：<strong>{{ activeTraceId || '-' }}</strong></div>
      <el-table :data="traceSpans" border stripe v-loading="traceLoading" empty-text="暂无链路明细">
        <el-table-column prop="eventTime" label="时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column prop="logType" label="日志类型" width="110" />
        <el-table-column prop="eventType" label="事件类型" min-width="140" show-overflow-tooltip />
        <el-table-column prop="sessionId" label="Session" min-width="150" show-overflow-tooltip />
        <el-table-column prop="modelRoute" label="模型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="latencyMs" label="延迟(ms)" width="110" />
        <el-table-column prop="totalTokens" label="Tokens" width="100" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="outputText" label="模型输出" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="output-text">{{ row.outputText || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportLogs, queryRecords, queryTrace } from '@/api/llmLog'

type TimeRange = [string, string] | []

const filters = reactive<{
  tenantId: string
  userId: string
  modelRoute: string
  status: string
  errorCode: string
  keyword: string
  minLatency: number | null
  maxLatency: number | null
  timeRange: TimeRange
}>({
  tenantId: '',
  userId: '',
  modelRoute: '',
  status: '',
  errorCode: '',
  keyword: '',
  minLatency: null,
  maxLatency: null,
  timeRange: []
})

const loading = ref(false)
const exporting = ref(false)
const rows = ref<Array<Record<string, unknown>>>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const traceDrawerVisible = ref(false)
const traceLoading = ref(false)
const activeTraceId = ref('')
const traceSpans = ref<Array<Record<string, unknown>>>([])

const buildQuery = () => {
  const [startAt, endAt] = filters.timeRange
  return {
    startAt: startAt || undefined,
    endAt: endAt || undefined,
    tenantId: filters.tenantId || undefined,
    userId: filters.userId || undefined,
    modelRoute: filters.modelRoute || undefined,
    status: filters.status || undefined,
    errorCode: filters.errorCode || undefined,
    keyword: filters.keyword || undefined,
    minLatency: filters.minLatency ?? undefined,
    maxLatency: filters.maxLatency ?? undefined,
    page: pageNo.value - 1,
    size: pageSize.value
  }
}

const loadRecords = async () => {
  loading.value = true
  try {
    const result = await queryRecords(buildQuery())
    rows.value = result?.content ?? []
    total.value = Number(result?.totalElements ?? 0)
  } catch {
    ElMessage.error('审计日志加载失败')
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleExport = async () => {
  exporting.value = true
  try {
    const [startAt, endAt] = filters.timeRange
    const response = await exportLogs({
      filter: {
        startAt: startAt || undefined,
        endAt: endAt || undefined,
        tenantId: filters.tenantId || undefined,
        userId: filters.userId || undefined,
        modelRoute: filters.modelRoute || undefined,
        status: filters.status || undefined,
        errorCode: filters.errorCode || undefined,
        keyword: filters.keyword || undefined,
        minLatency: filters.minLatency ?? undefined,
        maxLatency: filters.maxLatency ?? undefined
      },
      format: 'csv',
      limit: 1000
    })
    if (response?.downloadUrl) {
      window.open(response.downloadUrl, '_blank')
      ElMessage.success(`导出任务已生成：${response.fileName || 'llm-logs-export.csv'}`)
    } else {
      ElMessage.warning('导出接口返回为空')
    }
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const openTrace = async (traceId: string) => {
  if (!traceId) return
  activeTraceId.value = traceId
  traceDrawerVisible.value = true
  traceLoading.value = true
  traceSpans.value = []
  try {
    const result = await queryTrace(traceId)
    traceSpans.value = result?.spans ?? []
  } catch {
    ElMessage.error('Trace 详情加载失败')
    traceSpans.value = []
  } finally {
    traceLoading.value = false
  }
}

const handlePageChange = (next: number) => {
  pageNo.value = next
  void loadRecords()
}

const handleSizeChange = (next: number) => {
  pageSize.value = next
  pageNo.value = 1
  void loadRecords()
}

const resetFilters = () => {
  filters.tenantId = ''
  filters.userId = ''
  filters.modelRoute = ''
  filters.status = ''
  filters.errorCode = ''
  filters.keyword = ''
  filters.minLatency = null
  filters.maxLatency = null
  filters.timeRange = []
  pageNo.value = 1
  void loadRecords()
}

const formatTime = (value: unknown) => {
  if (value === null || value === undefined) return '-'
  const ts = Number(value)
  if (Number.isNaN(ts)) return String(value)
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(() => {
  void loadRecords()
})
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.sep {
  margin: 0 8px;
  color: #909399;
}

.hint {
  color: #909399;
  font-size: 12px;
}

.pagination-wrap {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.trace-meta {
  margin-bottom: 10px;
  color: #606266;
}

.output-text {
  display: inline-block;
  width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
