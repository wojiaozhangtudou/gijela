<template>
  <div class="llm-logs-container">
    <aside class="llm-sidebar">
      <div class="sidebar-header">
        <h2>LLM 日志</h2>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/chat/logs/overview" class="nav-item" :class="{ active: isActive('overview') }">
          <el-icon><DataAnalysis /></el-icon>
          <span>SRE 概览</span>
        </router-link>
        <router-link to="/chat/logs/troubleshoot" class="nav-item" :class="{ active: isActive('troubleshoot') }">
          <el-icon><Search /></el-icon>
          <span>问题排查</span>
        </router-link>
        <router-link to="/chat/logs/audit" class="nav-item" :class="{ active: isActive('audit') }">
          <el-icon><Document /></el-icon>
          <span>审计日志</span>
        </router-link>
        <router-link to="/chat/logs/alerts" class="nav-item" :class="{ active: isActive('alerts') }">
          <el-icon><Warning /></el-icon>
          <span>告警管理</span>
        </router-link>
      </nav>
    </aside>

    <main class="llm-content">
      <div class="content-header">
        <h1>{{ pageTitle }}</h1>
        <div class="header-actions">
          <el-button
            type="danger"
            plain
            :loading="clearing"
            @click="handleClearLogs"
          >
            一键清除日志
          </el-button>
          <el-button @click="goConsole">返回控制台</el-button>
          <el-button :loading="loading" @click="handleRefresh">刷新</el-button>
        </div>
      </div>
      <div class="content-body">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, Search, Document, Warning } from '@element-plus/icons-vue'
import { clearLogs } from '@/api/llmLog'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const clearing = ref(false)

const pageTitle = computed(() => {
  if (route.path.endsWith('/troubleshoot')) return '问题排查'
  if (route.path.endsWith('/audit')) return '审计日志'
  if (route.path.endsWith('/alerts')) return '告警管理'
  return 'SRE 概览'
})

const isActive = (segment: string) => route.path.includes(segment)

const handleRefresh = async () => {
  loading.value = true
  await new Promise(resolve => setTimeout(resolve, 300))
  loading.value = false
}

const goConsole = () => {
  void router.push('/')
}

const handleClearLogs = async () => {
  try {
    await ElMessageBox.confirm(
      '将清空当前日志看板对应日志（含运行日志、访问日志、审计日志），该操作不可恢复。是否继续？',
      '确认清理日志',
      {
        type: 'warning',
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true
      }
    )
  } catch {
    return
  }

  clearing.value = true
  try {
    const result = await clearLogs()
    if (result?.success === false) {
      ElMessage.error(result.message || '日志清理失败')
      return
    }
    ElMessage.success(`日志清理完成，删除 ${Number(result?.deletedCount ?? 0)} 条`)
    window.location.reload()
  } catch {
    ElMessage.error('日志清理失败，请稍后重试')
  } finally {
    clearing.value = false
  }
}
</script>

<style scoped>
.llm-logs-container {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
  overflow: hidden;
}

.llm-sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #ebeef5;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  padding: 12px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  color: #606266;
  text-decoration: none;
}

.nav-item.active,
.nav-item:hover {
  background: #ecf5ff;
  color: #409eff;
}

.llm-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.content-header h1 {
  margin: 0;
  font-size: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content-body {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow: auto;
}
</style>
