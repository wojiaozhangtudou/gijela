import { createRouter, createWebHistory } from 'vue-router'
import ChatPage from '@/views/ChatPage.vue'
import GraphPage from '@/views/GraphPage.vue'
import KnowledgePage from '@/views/KnowledgePage.vue'
import StoragePage from '@/views/StoragePage.vue'
import AttachmentPage from '@/views/AttachmentPage.vue'
import SkillListPage from '@/views/SkillListPage.vue'
import McpServerListPage from '@/views/McpServerListPage.vue'
import PromptManagePage from '@/views/PromptManagePage.vue'
import ModelConfigPage from '@/views/ModelConfigPage.vue'
import { llmLogsRoutes } from '@/views/llm-logs'

export default createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/overview',
      redirect: '/chat/logs/overview'
    },
    {
      path: '/',
      name: 'ChatPage',
      component: ChatPage
    },
    {
      path: '/knowledge',
      name: 'KnowledgePage',
      component: KnowledgePage
    },
    {
      path: '/graph',
      name: 'GraphPage',
      component: GraphPage
    },
    {
      path: '/storage',
      name: 'StoragePage',
      component: StoragePage
    },
    {
      path: '/attachments',
      name: 'AttachmentPage',
      component: AttachmentPage
    },
    {
      path: '/skills',
      name: 'SkillListPage',
      component: SkillListPage
    },
    {
      path: '/mcp-servers',
      name: 'McpServerListPage',
      component: McpServerListPage
    },
    {
      path: '/prompts',
      name: 'PromptManagePage',
      component: PromptManagePage
    },
    {
      path: '/model-configs',
      name: 'ModelConfigPage',
      component: ModelConfigPage
    },
    ...llmLogsRoutes
  ]
})
