<template>
  <div class="troubleshoot-page">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <span>快速排查</span>
          <div class="panel-actions">
            <el-button @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>

      <el-form :model="filters" inline>
        <el-form-item label="Trace ID">
          <el-input v-model="filters.traceId" clearable placeholder="输入 traceId 直接查看链路" style="width: 280px" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="filters.modelRoute" clearable placeholder="如 gpt-4o" style="width: 180px" />
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
        <el-form-item>
          <el-button type="primary" @click="handleTraceLookup">查链路</el-button>
          <el-button @click="loadData">查询失败</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>Top 慢请求</template>
          <el-table :data="topSlow" border stripe v-loading="loadingTopSlow" max-height="340">
            <el-table-column prop="traceId" label="Trace ID" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <el-button link type="primary" @click="openTrace(String(row.traceId || ''))">
                  {{ row.traceId || '-' }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="modelRoute" label="模型" width="120" />
            <el-table-column prop="value" label="延迟(ms)" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>延迟分布</template>
          <el-table :data="latencyDistribution" border stripe v-loading="loadingDistribution" max-height="340">
            <el-table-column prop="label" label="桶" min-width="120" />
            <el-table-column prop="from" label="From" width="120" />
            <el-table-column prop="to" label="To" width="120" />
            <el-table-column prop="count" label="数量" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <span>最近失败请求</span>
          <span class="hint">默认筛选 status=FAILED，支持点击 Trace 查看链路</span>
        </div>
      </template>
      <el-table :data="failedRows" border stripe v-loading="loadingFailed">
        <el-table-column prop="eventTime" label="时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="openTrace(String(row.traceId || ''))">
              {{ row.traceId || '-' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="modelRoute" label="模型" width="120" />
        <el-table-column prop="latencyMs" label="延迟(ms)" width="110" />
        <el-table-column prop="totalTokens" label="Tokens" width="100" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110" />
      </el-table>
    </el-card>

    <el-drawer v-model="traceDrawerVisible" title="链路详情" size="46%">
      <div class="trace-meta">Trace ID：<strong>{{ activeTraceId || '-' }}</strong></div>
      <el-table :data="traceSpans" border stripe v-loading="traceLoading" empty-text="暂无链路数据">
        <el-table-column prop="eventTime" label="时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column prop="modelRoute" label="模型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="latencyMs" label="延迟(ms)" width="110" />
        <el-table-column prop="totalTokens" label="Tokens" width="100" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryDistribution, queryRecords, queryTopN, queryTrace } from '@/api/llmLog'

type TimeRange = [string, string] | []

const filters = reactive<{
  traceId: string
  modelRoute: string
  timeRange: TimeRange
}>({
  traceId: '',
  modelRoute: '',
  timeRange: []
})

const loadingTopSlow = ref(false)
const loadingDistribution = ref(false)
const loadingFailed = ref(false)
const topSlow = ref<Array<Record<string, unknown>>>([])
const latencyDistribution = ref<Array<Record<string, unknown>>>([])
const failedRows = ref<Array<Record<string, unknown>>>([])

const traceDrawerVisible = ref(false)
const traceLoading = ref(false)
const activeTraceId = ref('')
const traceSpans = ref<Array<Record<string, unknown>>>([])

const buildFilter = (failedOnly = false) => {
  const [startAt, endAt] = filters.timeRange
  return {
    startAt: startAt || undefined,
    endAt: endAt || undefined,
    modelRoute: filters.modelRoute || undefined,
    status: failedOnly ? 'FAILED' : undefined
  }
}

const loadTopSlow = async () => {
  loadingTopSlow.value = true
  try {
    const result = await queryTopN({
      filter: buildFilter(false),
      metric: 'p95Latency',
      sortOrder: 'desc',
      limit: 10
    })
    topSlow.value = (result?.items ?? []).map((item: any) => ({
      traceId: item.traceId,
      modelRoute: item.modelRoute,
      value: item.value,
      status: item?.extras?.status ?? '-'
    }))
  } catch {
    ElMessage.error('Top 慢请求加载失败')
    topSlow.value = []
  } finally {
    loadingTopSlow.value = false
  }
}

const loadDistribution = async () => {
  loadingDistribution.value = true
  try {
    const result = await queryDistribution({
      filter: buildFilter(false),
      metric: 'latencyMs',
      buckets: 10
    })
    latencyDistribution.value = result?.buckets ?? []
  } catch {
    ElMessage.error('延迟分布加载失败')
    latencyDistribution.value = []
  } finally {
    loadingDistribution.value = false
  }
}

const loadFailedRows = async () => {
  loadingFailed.value = true
  try {
    const result = await queryRecords({
      ...buildFilter(true),
      page: 0,
      size: 20
    })
    failedRows.value = result?.content ?? []
  } catch {
    ElMessage.error('失败请求加载失败')
    failedRows.value = []
  } finally {
    loadingFailed.value = false
  }
}

const loadData = async () => {
  await Promise.all([loadTopSlow(), loadDistribution(), loadFailedRows()])
}

const openTrace = async (traceId: string) => {
  if (!traceId) {
    ElMessage.warning('Trace ID 为空')
    return
  }
  activeTraceId.value = traceId
  traceDrawerVisible.value = true
  traceLoading.value = true
  traceSpans.value = []
  try {
    const result = await queryTrace(traceId)
    traceSpans.value = result?.spans ?? []
  } catch {
    ElMessage.error('链路详情加载失败')
    traceSpans.value = []
  } finally {
    traceLoading.value = false
  }
}

const handleTraceLookup = async () => {
  await openTrace((filters.traceId || '').trim())
}

const resetFilters = () => {
  filters.traceId = ''
  filters.modelRoute = ''
  filters.timeRange = []
  void loadData()
}

const formatTime = (value: unknown) => {
  if (value === null || value === undefined) return '-'
  const ts = Number(value)
  if (Number.isNaN(ts)) return String(value)
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
.troubleshoot-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.hint {
  color: #909399;
  font-size: 12px;
}

.trace-meta {
  margin-bottom: 10px;
  color: #606266;
}
</style>
