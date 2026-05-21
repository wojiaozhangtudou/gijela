import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/workflows' },
  { path: '/workflows', component: () => import('../views/workflow/WorkflowList.vue') },
  { path: '/chat-workbench', component: () => import('../views/workflow/ChatflowWorkbench.vue') },
  { path: '/llm-models', component: () => import('../views/workflow/LlmModelList.vue') },
  { path: '/workflows/:id/editor', component: () => import('../views/workflow/WorkflowEditor.vue') },
  { path: '/workflows/:id/runs', component: () => import('../views/workflow/WorkflowRuns.vue') }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
