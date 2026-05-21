import client from './client'

export interface LogQueryFilter {
  startAt?: string
  endAt?: string
  tenantId?: string
  userId?: string
  modelRoute?: string
  status?: string
  errorCode?: string
  keyword?: string
  minLatency?: number
  maxLatency?: number
  sampled?: boolean
}

export interface TimeSeriesRequest {
  filter?: LogQueryFilter
  metric: string
  interval: string
  groupByModel?: boolean
  groupByStatus?: boolean
}

export interface AlertRule {
  id: number
  name: string
  description?: string
  metricType: string
  condition: string
  threshold: number
  windowMinutes: number
  severity: 'info' | 'warning' | 'critical'
  enabled: number
  notifyChannels: string[]
  notifyRecipients?: Record<string, string[]>
  lastTriggeredAt?: string
  recentEventCount?: number
}

export interface AlertEvent {
  id: number
  ruleId: number
  ruleName: string
  triggeredAt: string
  metricValue: number
  message: string
  status: 'open' | 'ack' | 'resolved'
  alertLevel: 'info' | 'warning' | 'critical'
  ackBy?: string
  ackAt?: string
  resolvedBy?: string
  resolvedAt?: string
  notificationCount?: number
}

export interface AlertRulePayload {
  name: string
  description?: string
  enabled?: number
  metricType: string
  condition: string
  threshold: number
  windowMinutes: number
  groupBy?: string
  filterJson?: string
  severity: 'info' | 'warning' | 'critical'
  cooldownMinutes?: number
  notifyChannels: string[]
  notifyRecipients: Record<string, string[]>
}

export interface AlertEventQuery {
  pageNo: number
  pageSize: number
  status?: string
  ruleId?: number
}

export interface LogClearResponse {
  success?: boolean
  deletedCount?: number
  message?: string
}

export const queryTimeseries = async (data: TimeSeriesRequest) => {
  const response = await client.post('/v1/llm-logs/query/timeseries', data)
  return response.data?.data ?? response.data
}

export const queryTopN = async (data: Record<string, unknown>) => {
  const response = await client.post('/v1/llm-logs/query/topn', data)
  return response.data?.data ?? response.data
}

export const queryDistribution = async (data: Record<string, unknown>) => {
  const response = await client.post('/v1/llm-logs/query/distribution', data)
  return response.data?.data ?? response.data
}

export const queryRecords = async (params: Record<string, unknown>) => {
  const response = await client.get('/v1/llm-logs/query/records', { params })
  return response.data?.data ?? response.data
}

export const queryTrace = async (traceId: string) => {
  const response = await client.get(`/v1/llm-logs/query/trace/${traceId}`)
  return response.data?.data ?? response.data
}

export const exportLogs = async (data: Record<string, unknown>) => {
  const response = await client.post('/v1/llm-logs/export', data)
  return response.data?.data ?? response.data
}

export const clearLogs = async (filter?: LogQueryFilter) => {
  const response = await client.post('/v1/llm-logs/maintenance/clear', filter ?? {})
  return (response.data?.data ?? response.data) as LogClearResponse
}

export const listAlertRules = async () => {
  const response = await client.get('/v1/llm-logs/alerts/rules')
  return response.data?.data ?? response.data
}

export const createAlertRule = async (data: AlertRulePayload) => {
  const response = await client.post('/v1/llm-logs/alerts/rules', data)
  return response.data?.data ?? response.data
}

export const updateAlertRule = async (id: number, data: AlertRulePayload) => {
  const response = await client.put(`/v1/llm-logs/alerts/rules/${id}`, data)
  return response.data?.data ?? response.data
}

export const toggleAlertRule = async (id: number, enabled: boolean) => {
  const response = await client.post(`/v1/llm-logs/alerts/rules/${id}/toggle`, { enabled })
  return response.data?.data ?? response.data
}

export const triggerAlertRule = async (id: number) => {
  const response = await client.post(`/v1/llm-logs/alerts/rules/${id}/trigger`)
  return response.data?.data ?? response.data
}

export const pageAlertEvents = async (data: AlertEventQuery) => {
  const response = await client.post('/v1/llm-logs/alerts/events/page', data)
  return response.data?.data ?? response.data
}

export const ackAlertEvent = async (id: number, comment?: string) => {
  const response = await client.post(`/v1/llm-logs/alerts/events/${id}/ack`, { comment })
  return response.data?.data ?? response.data
}

export const resolveAlertEvent = async (id: number, comment?: string) => {
  const response = await client.post(`/v1/llm-logs/alerts/events/${id}/resolve`, { comment })
  return response.data?.data ?? response.data
}

export const getMockTimeseriesData = (metric: string, interval: string) => {
  const now = Date.now()
  const buckets = [] as Array<{ timestamp: number; time: string; value: number; label: string }>
  const count = interval === '1h' ? 24 : 12
  const step = interval === '1h' ? 3600000 : 300000

  for (let i = 0; i < count; i += 1) {
    const timestamp = now - (count - i) * step
    const value = metric === 'errorRate'
      ? Math.round(Math.random() * 100) / 100
      : Math.round((80 + Math.random() * 60) * 100) / 100
    buckets.push({
      timestamp,
      time: new Date(timestamp).toLocaleString('zh-CN'),
      value,
      label: metric
    })
  }

  return {
    metric,
    interval,
    buckets,
    totalCount: buckets.length,
    queryTimeMs: 35
  }
}

export const getMockRecordsData = (page: number, size: number) => ({
  content: Array.from({ length: size }).map((_, index) => ({
    traceId: `trace-${String((page - 1) * size + index + 1).padStart(6, '0')}`,
    sessionId: `session-${page}-${index + 1}`,
    modelRoute: ['gpt-4', 'gpt-4o', 'claude-3'][index % 3],
    status: index % 5 === 0 ? 'FAILED' : 'SUCCESS',
    latencyMs: 200 + index * 80,
    totalTokens: 800 + index * 20,
    eventTime: Date.now() - index * 60000
  })),
  totalElements: 1000,
  totalPages: Math.ceil(1000 / size),
  number: page - 1,
  size
})

export const getMockAlertRules = (): AlertRule[] => ([
  {
    id: 1,
    name: '错误率过高',
    description: '5 分钟窗口内错误率超过 5% 时触发',
    metricType: 'errorRate',
    condition: 'gt',
    threshold: 5,
    windowMinutes: 5,
    severity: 'critical',
    enabled: 1,
    notifyChannels: ['wechat', 'email'],
    notifyRecipients: {
      wechat: ['llm-alert-group'],
      email: ['ops@gijela.local']
    },
    lastTriggeredAt: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
    recentEventCount: 3
  },
  {
    id: 2,
    name: 'P95 延迟抖动',
    description: 'P95 延迟超过 1500ms 时触发',
    metricType: 'p95Latency',
    condition: 'gt',
    threshold: 1500,
    windowMinutes: 10,
    severity: 'warning',
    enabled: 1,
    notifyChannels: ['dingding'],
    notifyRecipients: {
      dingding: ['llm-perf-robot']
    },
    lastTriggeredAt: new Date(Date.now() - 40 * 60 * 1000).toISOString(),
    recentEventCount: 1
  },
  {
    id: 3,
    name: '写入失败监控',
    description: 'ES 写入失败率持续升高',
    metricType: 'esIngestFailure',
    condition: 'gt',
    threshold: 1,
    windowMinutes: 5,
    severity: 'warning',
    enabled: 0,
    notifyChannels: ['wechat'],
    notifyRecipients: {
      wechat: ['llm-es-group']
    },
    recentEventCount: 0
  }
])

export const getMockAlertEvents = (pageNo = 1, pageSize = 10) => {
  const allEvents: AlertEvent[] = [
    {
      id: 1001,
      ruleId: 1,
      ruleName: '错误率过高',
      triggeredAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
      metricValue: 7.32,
      message: '[CRITICAL] 错误率过高 触发告警：errorRate gt 7.32 (阈值 5)',
      status: 'open',
      alertLevel: 'critical',
      notificationCount: 2
    },
    {
      id: 1002,
      ruleId: 2,
      ruleName: 'P95 延迟抖动',
      triggeredAt: new Date(Date.now() - 32 * 60 * 1000).toISOString(),
      metricValue: 1680.45,
      message: '[WARNING] P95 延迟抖动 触发告警：p95Latency gt 1680.45 (阈值 1500)',
      status: 'ack',
      alertLevel: 'warning',
      ackBy: 'ops.oncall',
      ackAt: new Date(Date.now() - 28 * 60 * 1000).toISOString(),
      notificationCount: 1
    },
    {
      id: 1003,
      ruleId: 1,
      ruleName: '错误率过高',
      triggeredAt: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString(),
      metricValue: 5.64,
      message: '[CRITICAL] 错误率过高 触发告警：errorRate gt 5.64 (阈值 5)',
      status: 'resolved',
      alertLevel: 'critical',
      resolvedBy: 'ops.lead',
      resolvedAt: new Date(Date.now() - 5.5 * 60 * 60 * 1000).toISOString(),
      notificationCount: 2
    }
  ]
  const start = (pageNo - 1) * pageSize
  const content = allEvents.slice(start, start + pageSize)
  return {
    content,
    totalElements: allEvents.length,
    totalPages: Math.ceil(allEvents.length / pageSize),
    number: pageNo - 1,
    size: pageSize
  }
}
