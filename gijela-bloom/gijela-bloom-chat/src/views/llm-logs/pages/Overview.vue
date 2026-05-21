<template>
  <div class="overview-page">
    <el-row :gutter="20" class="kpi-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in cards" :key="card.label">
        <div class="kpi-card">
          <p class="kpi-label">{{ card.label }}</p>
          <p class="kpi-value">{{ card.value }}</p>
          <p class="kpi-trend">{{ card.trend }}</p>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>QPS 趋势</template>
          <div ref="qpsChartRef" class="chart-placeholder"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>错误率趋势</template>
          <div ref="errorRateChartRef" class="chart-placeholder"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="table-card">
      <template #header>Top 10 最慢请求</template>
      <el-table :data="slowRequests" stripe :loading="loading">
        <el-table-column prop="traceId" label="Trace ID" min-width="180">
          <template #default="{ row }">
            <el-button
              v-if="row.traceId"
              link
              type="primary"
              @click="openTraceDetail(String(row.traceId))"
            >
              {{ row.traceId }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="modelRoute" label="模型" width="120" />
        <el-table-column prop="latencyMs" label="延迟(ms)" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!row.traceId"
              @click="openTraceDetail(String(row.traceId))"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="traceDrawerVisible" title="日志详情" size="48%">
      <template v-if="activeTraceId">
        <div class="trace-meta">
          <span>Trace ID：</span>
          <strong>{{ activeTraceId }}</strong>
        </div>
      </template>
      <el-table :data="traceSpans" stripe border v-loading="traceLoading" empty-text="暂无链路明细">
        <el-table-column prop="eventTime" label="时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.eventTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="modelRoute" label="模型" width="140" />
        <el-table-column prop="latencyMs" label="延迟(ms)" width="120" />
        <el-table-column prop="totalTokens" label="Tokens" width="100" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryTimeseries, queryTopN, queryTrace } from '@/api/llmLog'

const loading = ref(false)
const qpsSeries = ref<Array<{ timestamp?: number; time?: string; value?: number }>>([])
const errorRateSeries = ref<Array<{ timestamp?: number; time?: string; value?: number }>>([])
const p95Series = ref<Array<{ timestamp?: number; time?: string; value?: number }>>([])
const firstTokenSeries = ref<Array<{ timestamp?: number; time?: string; value?: number }>>([])
const slowRequests = ref<Array<Record<string, unknown>>>([])
const traceDrawerVisible = ref(false)
const traceLoading = ref(false)
const activeTraceId = ref('')
const traceSpans = ref<Array<Record<string, unknown>>>([])
const qpsChartRef = ref<HTMLDivElement | null>(null)
const errorRateChartRef = ref<HTMLDivElement | null>(null)
const totalTokensSeries = ref<Array<{ timestamp?: number; time?: string; value?: number }>>([])
let qpsChart: echarts.ECharts | null = null
let errorRateChart: echarts.ECharts | null = null

const cards = computed(() => [
  {
    label: '平均 QPS',
    value: formatNumber(avg(qpsSeries.value), 2),
    trend: `最近点 ${formatNumber(last(qpsSeries.value), 2)}`
  },
  {
    label: '错误率',
    value: `${formatNumber(avg(errorRateSeries.value), 2)}%`,
    trend: `最近点 ${formatNumber(last(errorRateSeries.value), 2)}%`
  },
  {
    label: 'P95 延迟',
    value: `${formatNumber(avg(p95Series.value), 0)}ms`,
    trend: `最近点 ${formatNumber(last(p95Series.value), 0)}ms`
  },
  {
    label: '首 Token',
    value: `${formatNumber(avg(firstTokenSeries.value), 0)}ms`,
    trend: `最近点 ${formatNumber(last(firstTokenSeries.value), 0)}ms`
  },
  {
    label: '消费 Token 数',
    value: formatNumber(totalTokensSum(totalTokensSeries.value), 0),
    trend: `最近点 ${formatNumber(last(totalTokensSeries.value), 0)}`
  }
])

const loadData = async () => {
  loading.value = true
  try {
    const [qps, errorRate, p95Latency, firstToken, totalTokens, topLatency] = await Promise.all([
      queryTimeseries({ metric: 'qps', interval: '1h' }),
      queryTimeseries({ metric: 'errorRate', interval: '1h' }),
      queryTimeseries({ metric: 'p95Latency', interval: '1h' }),
      queryTimeseries({ metric: 'avgFirstToken', interval: '1h' }),
      queryTimeseries({ metric: 'totalTokens', interval: '1h' }),
      queryTopN({ metric: 'p95Latency', limit: 10, sortOrder: 'desc' })
    ])

    qpsSeries.value = qps?.buckets ?? []
    errorRateSeries.value = errorRate?.buckets ?? []
    p95Series.value = p95Latency?.buckets ?? []
    firstTokenSeries.value = firstToken?.buckets ?? []
    totalTokensSeries.value = totalTokens?.buckets ?? []

    slowRequests.value = (topLatency?.items ?? []).map((item: any) => ({
      traceId: item.traceId,
      modelRoute: item.modelRoute,
      latencyMs: item.value,
      status: item?.extras?.status ?? '-'
    }))
  } catch (error) {
    ElMessage.error('日志看板数据加载失败，请检查后端接口或 ES 配置')
    qpsSeries.value = []
    errorRateSeries.value = []
    p95Series.value = []
    firstTokenSeries.value = []
    totalTokensSeries.value = []
    slowRequests.value = []
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

const renderCharts = () => {
  if (qpsChartRef.value) {
    qpsChart ??= echarts.init(qpsChartRef.value)
    qpsChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: qpsSeries.value.map(item => item.time ?? '-') },
      yAxis: { type: 'value' },
      grid: { left: 36, right: 20, top: 30, bottom: 30 },
      series: [{
        name: 'QPS',
        type: 'line',
        smooth: true,
        areaStyle: {},
        data: qpsSeries.value.map(item => item.value ?? 0),
        color: '#409eff'
      }]
    })
  }

  if (errorRateChartRef.value) {
    errorRateChart ??= echarts.init(errorRateChartRef.value)
    errorRateChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: errorRateSeries.value.map(item => item.time ?? '-') },
      yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
      grid: { left: 36, right: 20, top: 30, bottom: 30 },
      series: [{
        name: '错误率',
        type: 'line',
        smooth: true,
        data: errorRateSeries.value.map(item => item.value ?? 0),
        color: '#f56c6c'
      }]
    })
  }
}

const openTraceDetail = async (traceId: string) => {
  if (!traceId) return
  activeTraceId.value = traceId
  traceDrawerVisible.value = true
  traceLoading.value = true
  traceSpans.value = []
  try {
    const trace = await queryTrace(traceId)
    traceSpans.value = trace?.spans ?? []
  } catch (error) {
    ElMessage.error('日志详情加载失败')
    traceSpans.value = []
  } finally {
    traceLoading.value = false
  }
}

function avg(values: Array<{ value?: number }>) {
  if (!values.length) return 0
  const total = values.reduce((sum, item) => sum + Number(item.value ?? 0), 0)
  return total / values.length
}

function last(values: Array<{ value?: number }>) {
  if (!values.length) return 0
  return Number(values[values.length - 1].value ?? 0)
}

function totalTokensSum(values: Array<{ value?: number }>) {
  if (!values.length) return 0
  return values.reduce((sum, item) => sum + Number(item.value ?? 0), 0)
}

function formatNumber(value: number, digits = 2) {
  return Number.isFinite(value) ? value.toFixed(digits) : '0'
}

function formatTime(value: unknown) {
  if (value === null || value === undefined) return '-'
  const ts = Number(value)
  if (Number.isNaN(ts)) return String(value)
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(loadData)

onBeforeUnmount(() => {
  qpsChart?.dispose()
  errorRateChart?.dispose()
})
</script>

<style scoped>
.overview-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.kpi-row {
  margin-bottom: 0;
}

.kpi-card {
  padding: 18px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.kpi-label {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.kpi-value {
  margin: 10px 0 6px;
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}

.kpi-trend {
  margin: 0;
  color: #67c23a;
  font-size: 12px;
}

.chart-placeholder {
  height: 260px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #909399;
}

.table-card {
  margin-top: 4px;
}

.trace-meta {
  margin-bottom: 12px;
  color: #606266;
}
</style>
