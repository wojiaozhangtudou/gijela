<template>
  <el-card shadow="never" class="chat-window">
    <template #header>
      <div class="chat-title">对话窗口</div>
    </template>
    <el-scrollbar ref="scrollbarRef" class="message-scroll" @scroll="handleScroll">
      <div class="message-list">
        <div
          v-for="(item, index) in messages"
          :key="`${item.role}-${index}`"
          class="message-row"
          :class="item.role === 'user' ? 'is-right' : 'is-left'"
        >
          <div class="message-item" :class="[item.role, { failed: item.failed }]">
            <div class="message-meta">
              <span class="message-role">{{ roleLabel(item.role) }}</span>
              <span class="message-time">{{ formatTime(item.createdAt) }}</span>
              <span v-if="formatUsage(item.usage)" class="message-usage">{{ formatUsage(item.usage) }}</span>
              <el-tag v-if="item.failed" size="small" type="danger" effect="light" class="failed-tag">失败回合</el-tag>
              <el-button
                size="small"
                link
                type="primary"
                class="copy-raw-btn"
                @click="copyRawText(getRawMessage(item.content))"
              >
                复制原文
              </el-button>
            </div>
            <!-- 工具调用气泡（解析出的 tool_code 块） -->
            <template v-if="extractedToolCalls(item.content).length">
              <div
                v-for="(tc, ti) in extractedToolCalls(item.content)"
                :key="ti"
                class="tool-call-bubble"
              >
                <el-icon class="tool-icon"><i-ep-cpu /></el-icon>
                <span class="tool-name">{{ tc.name }}</span>
                <span v-if="tc.query" class="tool-query">「{{ tc.query }}」</span>
              </div>
            </template>
            <div class="message-content markdown-body" v-html="renderMarkdown(item.content)"></div>
            <div v-if="item.references?.length" class="message-references">
              <el-button
                type="primary"
                plain
                size="small"
                class="reference-entry-btn"
                @click="emit('openReferences', item.references)"
              >
                查看引用（{{ item.references.length }}）
              </el-button>
            </div>
          </div>
        </div>
        <!-- 流式工具调用实时气泡 -->
        <div v-if="activeToolCalls.length" class="message-row is-left">
          <div class="message-item assistant">
            <div v-for="(tc, ti) in activeToolCalls" :key="ti" class="tool-call-bubble calling">
              <el-icon class="tool-icon"><i-ep-loading /></el-icon>
              <span class="tool-name">{{ tc.name }}</span>
              <span v-if="tc.query" class="tool-query">「{{ tc.query }}」</span>
            </div>
          </div>
        </div>
        <div v-if="pendingText" class="message-row is-left">
          <div class="message-item assistant pending">
            <div class="message-meta">
              <span class="message-role">模型</span>
              <span class="message-time">{{ formatTime(pendingCreatedAt) }}</span>
              <span v-if="formatUsage(pendingUsage)" class="message-usage">{{ formatUsage(pendingUsage) }}</span>
              <el-button
                size="small"
                link
                type="primary"
                class="copy-raw-btn"
                @click="copyRawText(pendingText)"
              >
                复制原文
              </el-button>
            </div>
            <div class="message-content markdown-body" v-html="renderMarkdown(pendingText)"></div>
          </div>
        </div>
      </div>
    </el-scrollbar>
  </el-card>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { ElScrollbar } from 'element-plus'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import type { ChatMessage, ChatUsage } from '@/types/chat'

const props = defineProps<{
  messages: ChatMessage[]
  pendingText: string
  pendingCreatedAt: string
  pendingUsage?: ChatUsage | null
  activeToolCalls?: Array<{ name: string; query?: string }>
}>()

const activeToolCalls = computed(() => props.activeToolCalls ?? [])
const markdownRenderer = new MarkdownIt({ html: false, linkify: true, breaks: true })

// 从 content 中提取 <tool_code>{...}</tool_code> 块，解析 name / query
function extractedToolCalls(content: string): Array<{ name: string; query?: string }> {
  if (!content) return []
  const results: Array<{ name: string; query?: string }> = []
  const re = /<tool_code>\s*([\s\S]*?)\s*<\/tool_code>/g
  let match: RegExpExecArray | null
  while ((match = re.exec(content)) !== null) {
    try {
      const parsed = JSON.parse(match[1])
      if (parsed && parsed.name) {
        results.push({ name: parsed.name, query: parsed.arguments?.query })
      }
    } catch {
      results.push({ name: match[1].trim() })
    }
  }
  return results
}

// 去掉 <tool_code>...</tool_code> 块后返回纯文本
function stripToolCode(content: string): string {
  if (!content) return content
  return content.replace(/<tool_code>[\s\S]*?<\/tool_code>/g, '').trim()
}

function getRawMessage(content: string): string {
  return stripToolCode(content || '')
}

function normalizeMarkdownInput(raw: string): string {
  if (!raw) {
    return ''
  }
  return raw
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/&nbsp;/gi, ' ')
}

function renderMarkdown(content: string): string {
  const raw = normalizeMarkdownInput(getRawMessage(content))
  if (!raw) {
    return '<p></p>'
  }
  return markdownRenderer.render(raw)
}

async function copyRawText(raw: string) {
  const text = raw || ''
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制原文')
    return
  } catch {
    // fallback below
  }

  try {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', 'true')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制原文')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

const emit = defineEmits<{
  openReferences: [references: Array<{ id?: string | number; title?: string; score?: number; payload?: Record<string, unknown> }>]
}>()

const scrollbarRef = ref<InstanceType<typeof ElScrollbar> | null>(null)
const autoFollow = ref(true)

const followTrigger = computed(() => `${props.messages.length}:${props.pendingText.length}`)

watch(
  followTrigger,
  async () => {
    if (!autoFollow.value) {
      return
    }
    await nextTick()
    scrollToBottom()
  },
  { immediate: true }
)

function scrollToBottom() {
  const wrap = scrollbarRef.value?.wrapRef
  if (!wrap) {
    return
  }
  scrollbarRef.value?.setScrollTop(wrap.scrollHeight)
}

function handleScroll(payload: { scrollTop: number }) {
  const wrap = scrollbarRef.value?.wrapRef
  if (!wrap) {
    return
  }
  const threshold = 24
  const distanceToBottom = wrap.scrollHeight - (payload.scrollTop + wrap.clientHeight)
  autoFollow.value = distanceToBottom <= threshold
}

function roleLabel(role: ChatMessage['role']) {
  if (role === 'user') {
    return '我'
  }
  if (role === 'assistant') {
    return '模型'
  }
  return role
}

function formatTime(raw?: string) {
  if (!raw) {
    return ''
  }
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) {
    return raw
  }
  const yyyy = String(date.getFullYear())
  const MM = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${yyyy}-${MM}-${dd} ${hh}:${mm}:${ss}`
}

function formatUsage(usage?: ChatUsage | null) {
  if (!usage) {
    return ''
  }
  const total = typeof usage.totalTokens === 'number' ? usage.totalTokens : undefined
  const prompt = typeof usage.promptTokens === 'number' ? usage.promptTokens : undefined
  const completion = typeof usage.completionTokens === 'number' ? usage.completionTokens : undefined
  if (typeof total !== 'number' && typeof prompt !== 'number' && typeof completion !== 'number') {
    return ''
  }
  const parts = [] as string[]
  if (typeof total === 'number') {
    parts.push(`总 ${total}`)
  }
  if (typeof prompt === 'number') {
    parts.push(`输入 ${prompt}`)
  }
  if (typeof completion === 'number') {
    parts.push(`输出 ${completion}`)
  }
  return `Tokens：${parts.join(' / ')}`
}
</script>

<style scoped>
.chat-window {
  height: 100%;
  border-radius: 16px;
}

.tool-call-bubble {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 12px;
  padding: 2px 10px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #2f54eb;
}

.tool-call-bubble.calling {
  background: #fff7e6;
  border-color: #ffd591;
  color: #d46b08;
}

.tool-icon {
  font-size: 13px;
}

.tool-name {
  font-weight: 600;
}

.tool-query {
  opacity: 0.8;
  font-style: italic;
}

.chat-window :deep(.el-card__body) {
  height: calc(100% - 56px);
  padding-top: 8px;
  padding-bottom: 8px;
  overflow: hidden;
}

.message-scroll {
  height: 100%;
}

.chat-title {
  font-weight: 600;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  padding: 4px 0;
}

.message-row {
  display: flex;
}

.message-row.is-left {
  justify-content: flex-start;
}

.message-row.is-right {
  justify-content: flex-end;
}

.message-item {
  padding: 12px;
  border-radius: 12px;
  background: #f5f7fa;
  max-width: min(78%, 760px);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.message-item.user {
  background: linear-gradient(135deg, #3a8dff 0%, #2f78e3 100%);
  color: #fff;
  border-top-right-radius: 4px;
}

.message-item.assistant {
  background: #f7f8fa;
  border-top-left-radius: 4px;
}

.message-item.failed {
  border: 1px solid #f56c6c;
  background: #fef0f0;
}

.message-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-item.user .message-meta {
  color: rgba(255, 255, 255, 0.85);
}

.message-role {
  font-size: 12px;
  font-weight: 600;
}

.message-time {
  opacity: 0.9;
}

.message-usage {
  color: #606266;
}

.failed-tag {
  text-transform: none;
}

.message-content {
  line-height: 1.6;
}

.copy-raw-btn {
  margin-left: auto;
}

.markdown-body :deep(*) {
  word-break: break-word;
}

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 10px 0 6px;
  line-height: 1.35;
}

.markdown-body :deep(code) {
  background: rgba(27, 31, 35, 0.08);
  border-radius: 4px;
  padding: 2px 4px;
  font-size: 12px;
}

.markdown-body :deep(pre) {
  background: #eef1f6;
  border-radius: 6px;
  padding: 10px;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-references {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.reference-entry-btn {
  border-radius: 999px;
}
</style>
