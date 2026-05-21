<template>
  <div class="attachment-page">
    <div class="page-header">
      <div>
        <h1>附件管理</h1>
        <p>按会话查看文件、摘要与浓缩内容。</p>
      </div>
      <el-space>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <el-card shadow="never" class="main-card">
      <el-form inline>
        <el-form-item label="会话">
          <el-select v-model="sessionId" filterable placeholder="请选择会话" style="width: 420px" @change="reloadAttachments">
            <el-option v-for="item in sessions" :key="item.sessionId" :label="item.title || item.sessionId" :value="item.sessionId" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名">
          <el-input v-model="fileNameLike" placeholder="按文件名模糊搜索" clearable style="width: 220px" @keyup.enter="reloadAttachments" />
        </el-form-item>
        <el-form-item label="上传时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" clearable placeholder="全部" style="width: 140px" @change="reloadAttachments">
            <el-option label="待处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="失败" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要模型">
          <el-select v-model="summaryModel" filterable allow-create default-first-option placeholder="请选择或输入摘要模型" style="width: 240px">
            <el-option v-for="item in summaryModelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleUploadChange">
            <el-button type="success" :disabled="!sessionId">上传附件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" plain :disabled="!selectedIds.length" @click="deleteBatch">批量删除</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reloadAttachments">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-empty v-if="!sessionId" description="请先选择会话" :image-size="88" />

      <el-table v-else class="attachment-table" :data="rows" stripe v-loading="loading" empty-text="暂无附件" style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="scope">
            {{ formatSize(scope.row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="processStatus" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.processStatus)" effect="light">{{ statusText(scope.row.processStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="320" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="上传时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
            <el-button link type="success" @click="downloadOne(scope.row)">下载</el-button>
            <el-button link type="warning" @click="renameOne(scope.row)">重命名</el-button>
            <el-button link type="danger" @click="deleteOne(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-wrap" v-if="sessionId">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="附件详情" width="900px" destroy-on-close>
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="附件ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="会话ID">{{ detail.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ detail.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detail.processStatus) }}</el-descriptions-item>
          <el-descriptions-item label="对象键" :span="2">{{ detail.objectKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detail.summary || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">浓缩 Markdown</div>
        <el-scrollbar class="text-box">
          <div class="markdown-render" v-html="renderedCondensedMd"></div>
        </el-scrollbar>

        <div class="section-title section-title-row">
          <span>原文文本</span>
          <el-button size="small" type="primary" plain :disabled="!detail.rawText" @click="copyRawText">复制原文</el-button>
        </div>
        <el-scrollbar class="text-box raw-box">
          <pre>{{ detail.rawText || '-' }}</pre>
        </el-scrollbar>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { deleteAttachment, deleteAttachmentsBatch, downloadAttachment, fetchAttachmentDetail, fetchSessions, listModelConfigOptions, listSessionAttachments, renameAttachment, uploadAttachment } from '@/api/chat'
import type { ChatAttachmentDetail, ChatAttachmentItem, ChatSessionItem } from '@/types/chat'

const router = useRouter()
const sessions = ref<ChatSessionItem[]>([])
const sessionId = ref('')
const fileNameLike = ref('')
const dateRange = ref<[string, string] | null>(null)
const statusFilter = ref<number | undefined>(undefined)
const rows = ref<ChatAttachmentItem[]>([])
const selectedIds = ref<number[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<ChatAttachmentDetail | null>(null)
const summaryModelOptions = ref<Array<{ label: string; value: string }>>([
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'Qwen Max', value: 'qwen-max' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'GPT-4o Mini', value: 'gpt-4o-mini' }
])
const summaryModel = ref('qwen-plus')
const markdownRenderer = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedCondensedMd = computed(() => {
  const source = detail.value?.condensedMd?.trim()
  if (!source) {
    return '<p>-</p>'
  }
  return markdownRenderer.render(source)
})

onMounted(async () => {
  await loadSummaryModelOptions()
  await loadSessions()
})

async function loadSummaryModelOptions() {
  try {
    const options = await listModelConfigOptions('CHAT')
    if (!options.length) {
      return
    }
    summaryModelOptions.value = options.map(item => ({
      label: item.label || `${item.providerKey} / ${item.model}`,
      value: item.model
    }))
    if (!summaryModel.value) {
      summaryModel.value = summaryModelOptions.value[0]?.value || ''
    }
  } catch {
    // 回退内置模型选项
  }
}

async function loadSessions() {
  sessions.value = await fetchSessions(100)
  if (!sessionId.value && sessions.value.length) {
    sessionId.value = sessions.value[0].sessionId
    await reloadAttachments()
  }
}

async function reloadAttachments() {
  if (!sessionId.value) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await listSessionAttachments({
      sessionId: sessionId.value,
      page: page.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
      fileNameLike: fileNameLike.value || undefined,
      startTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1]
    })
    rows.value = result?.items || []
    total.value = Number(result?.total || 0)
    selectedIds.value = []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载附件列表失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  try {
    detail.value = await fetchAttachmentDetail(id)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载附件详情失败')
  }
}

async function handleUploadChange(file: { raw?: File }) {
  if (!sessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  if (!file.raw) {
    return
  }
  try {
    await uploadAttachment(file.raw, sessionId.value, summaryModel.value?.trim() || undefined)
    ElMessage.success('上传成功')
    page.value = 1
    await reloadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  }
}

async function renameOne(row: ChatAttachmentItem) {
  try {
    const currentName = row.fileName || ''
    const res = await ElMessageBox.prompt('请输入新的文件名', '重命名附件', {
      inputValue: currentName,
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const nextName = res.value?.trim()
    if (!nextName) {
      return
    }
    await renameAttachment(row.id, nextName)
    ElMessage.success('重命名成功')
    await reloadAttachments()
    if (detailVisible.value && detail.value?.id === row.id) {
      detail.value = await fetchAttachmentDetail(row.id)
    }
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '重命名失败')
  }
}

async function deleteOne(row: ChatAttachmentItem) {
  try {
    await ElMessageBox.confirm(`确认删除附件「${row.fileName || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteAttachment(row.id)
    ElMessage.success('删除成功')
    if (detailVisible.value && detail.value?.id === row.id) {
      detailVisible.value = false
      detail.value = null
    }
    if (rows.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await reloadAttachments()
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function deleteBatch() {
  if (!selectedIds.value.length) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个附件吗？`, '批量删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteAttachmentsBatch(selectedIds.value)
    ElMessage.success('批量删除成功')
    if (rows.value.length === selectedIds.value.length && page.value > 1) {
      page.value -= 1
    }
    await reloadAttachments()
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败')
  }
}

async function downloadOne(row: ChatAttachmentItem) {
  try {
    const response = await downloadAttachment(row.id)
    const contentTypeHeader = response.headers?.['content-type']
    const contentType = typeof contentTypeHeader === 'string' ? contentTypeHeader : 'application/octet-stream'
    const blob = new Blob([response.data], { type: contentType })
    const objectUrl = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = row.fileName || `attachment-${row.id}`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(objectUrl)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败')
  }
}

function handleSelectionChange(selection: ChatAttachmentItem[]) {
  selectedIds.value = selection.map((item) => item.id)
}

function onPageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  void reloadAttachments()
}

function onPageChange(nextPage: number) {
  page.value = nextPage
  void reloadAttachments()
}

function statusText(status?: number | null) {
  if (status === 0) return '待处理'
  if (status === 1) return '处理中'
  if (status === 2) return '已完成'
  if (status === 3) return '失败'
  return '-'
}

function statusTagType(status?: number | null) {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  if (status === 1) return 'warning'
  return 'info'
}

function formatSize(size?: number | null) {
  if (!size || size <= 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function copyRawText() {
  const rawText = detail.value?.rawText || ''
  if (!rawText) {
    ElMessage.warning('暂无原文可复制')
    return
  }

  try {
    await navigator.clipboard.writeText(rawText)
    ElMessage.success('原文已复制')
    return
  } catch {
    // ignore and fallback below
  }

  try {
    const textarea = document.createElement('textarea')
    textarea.value = rawText
    textarea.setAttribute('readonly', 'true')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('原文已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function goChatPage() {
  void router.push('/')
}
</script>

<style scoped>
.attachment-page {
  padding: 20px;
  min-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0;
  font-size: 22px;
}

.page-header p {
  margin: 4px 0 0;
  color: #909399;
}

.section-title {
  margin: 14px 0 8px;
  font-weight: 600;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.main-card {
  flex: 1;
  min-height: 0;
}

.main-card :deep(.el-card__body) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.attachment-table {
  flex: 1;
}

.pager-wrap {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.text-box {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  height: 180px;
  padding: 8px;
  background: #fafafa;
}

.raw-box {
  height: 220px;
}

.text-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}

.markdown-render {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
}

.markdown-render :deep(*) {
  word-break: break-word;
}

.markdown-render :deep(h1),
.markdown-render :deep(h2),
.markdown-render :deep(h3),
.markdown-render :deep(h4),
.markdown-render :deep(h5),
.markdown-render :deep(h6) {
  margin: 12px 0 8px;
  line-height: 1.4;
}

.markdown-render :deep(p) {
  margin: 6px 0;
}

.markdown-render :deep(ul),
.markdown-render :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-render :deep(code) {
  background: rgba(27, 31, 35, 0.05);
  border-radius: 4px;
  padding: 2px 4px;
  font-size: 12px;
}

.markdown-render :deep(pre) {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px;
  overflow-x: auto;
}

.markdown-render :deep(pre code) {
  background: transparent;
  padding: 0;
}
</style>
