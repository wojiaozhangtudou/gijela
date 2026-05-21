import type { RouteRecordRaw } from 'vue-router'

const LlmLogsLayout = () => import('./DashboardLayout.vue')
const OverviewPage = () => import('./pages/Overview.vue')
const TroubleshootPage = () => import('./pages/Troubleshoot.vue')
const AuditPage = () => import('./pages/Audit.vue')
const AlertsPage = () => import('./pages/Alerts.vue')

export const llmLogsRoutes: RouteRecordRaw[] = [
  {
    path: '/chat/logs',
    alias: ['/dashboard/chat/logs', '/dashboard/llm-logs'],
    component: LlmLogsLayout,
    meta: {
      title: 'LLM 日志'
    },
    children: [
      { path: '', redirect: 'overview' },
      { path: '/dashboard/chat/logs/overview', redirect: '/chat/logs/overview' },
      { path: '/dashboard/chat/logs/troubleshoot', redirect: '/chat/logs/troubleshoot' },
      { path: '/dashboard/chat/logs/audit', redirect: '/chat/logs/audit' },
      { path: '/dashboard/chat/logs/alerts', redirect: '/chat/logs/alerts' },
      { path: '/dashboard/llm-logs/overview', redirect: '/chat/logs/overview' },
      { path: '/dashboard/llm-logs/troubleshoot', redirect: '/chat/logs/troubleshoot' },
      { path: '/dashboard/llm-logs/audit', redirect: '/chat/logs/audit' },
      { path: '/dashboard/llm-logs/alerts', redirect: '/chat/logs/alerts' },
      { path: 'overview', component: OverviewPage, meta: { title: 'LLM 概览' } },
      { path: 'troubleshoot', component: TroubleshootPage, meta: { title: '问题排查' } },
      { path: 'audit', component: AuditPage, meta: { title: '审计日志' } },
      { path: 'alerts', component: AlertsPage, meta: { title: '告警管理' } }
    ]
  }
]
