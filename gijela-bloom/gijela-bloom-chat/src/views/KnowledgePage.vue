<template>
  <div class="knowledge-page">
    <div class="page-header">
      <div>
        <h1>知识库管理</h1>
        <p>独立完成知识入库与向量检索结果查看。</p>
      </div>
      <el-space>
        <el-button type="warning" plain @click="openManageDialog">集合/向量管理</el-button>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <el-row :gutter="16" class="knowledge-layout">
      <el-col :span="10" class="panel-col">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="panel-header">知识入库</div>
          </template>
          <div class="collection-init-block knowledge-field">
            <div class="collection-init-title">向量集合初始化</div>
            <el-select v-model="knowledgeForm.embeddingModel" filterable allow-create default-first-option class="knowledge-field" placeholder="请选择或输入向量模型">
              <el-option v-for="item in embeddingModelOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <div class="knowledge-row">
              <el-input-number v-model="collectionForm.vectorSize" :min="0" :max="8192" :step="1" controls-position="right" />
              <el-select v-model="collectionForm.distance" style="width: 130px">
                <el-option label="Cosine" value="Cosine" />
                <el-option label="Dot" value="Dot" />
                <el-option label="Euclid" value="Euclid" />
              </el-select>
            </div>
            <div class="knowledge-hint">向量维度填 0 表示由后端自动探测</div>
            <div class="knowledge-row">
              <el-button plain :loading="detectingDimension" @click="submitDetectDimension">自动探测维度</el-button>
              <el-button type="warning" plain :loading="initializingCollection" @click="submitInitCollection">创建/校验集合</el-button>
            </div>
            <div v-if="collectionResult" class="collection-result">{{ collectionResult }}</div>
          </div>

          <el-input v-model="knowledgeForm.title" placeholder="标题（可选）" class="knowledge-field" />
          <div class="knowledge-hint">知识入库将使用所选向量模型进行向量化；文件入库与文本入库共用该配置</div>

          <el-tabs v-model="activeIngestTab" class="knowledge-tabs knowledge-field">
            <el-tab-pane label="文本写入" name="text">
              <el-input
                v-model="knowledgeForm.content"
                type="textarea"
                :rows="5"
                placeholder="请输入要写入知识库的文本内容"
                class="knowledge-field"
              />
              <el-button type="primary" :loading="indexingKnowledge" :disabled="!knowledgeForm.content.trim()" @click="submitKnowledge">
                写入知识库
              </el-button>
            </el-tab-pane>
            <el-tab-pane label="文件写入" name="file">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleKnowledgeFileChange" class="knowledge-field">
                <el-button plain>选择文件入库（Tika 提取）</el-button>
              </el-upload>
              <div v-if="selectedFileName" class="file-name">已选文件：{{ selectedFileName }}</div>
              <el-button
                class="knowledge-field"
                type="primary"
                plain
                :loading="indexingKnowledgeFile"
                :disabled="!selectedFile"
                @click="submitKnowledgeFile"
              >
                文件入库
              </el-button>
            </el-tab-pane>
          </el-tabs>

          <div class="knowledge-row">
            <el-input-number v-model="knowledgeForm.chunkSize" :min="100" :max="4000" :step="50" controls-position="right" />
            <el-input-number v-model="knowledgeForm.chunkOverlap" :min="0" :max="1000" :step="10" controls-position="right" />
          </div>
          <div class="knowledge-hint">分块长度 / 重叠长度（文本/文件写入共用）</div>
          <div v-if="knowledgeResult" class="success-text">{{ knowledgeResult }}</div>

          <div v-if="knowledgeRecords.length" class="records-wrap">
            <div class="records-title">最近入库</div>
            <el-scrollbar class="records-scroll">
              <div v-for="item in knowledgeRecords" :key="item.id" class="record-item">
                <div class="record-main">
                  <span class="record-title">{{ item.title || '未命名文档' }}</span>
                  <span class="record-time">{{ item.time }}</span>
                </div>
                <div class="record-sub">chunks={{ item.chunks }} · {{ item.collection }}</div>
              </div>
            </el-scrollbar>
          </div>
        </el-card>
      </el-col>

      <el-col :span="14" class="panel-col">
        <el-card shadow="never" class="result-card">
          <template #header>
            <div class="panel-header">向量检索展示</div>
          </template>
          <div class="search-row">
            <el-input v-model="searchQuery" placeholder="输入检索问题，例如：权限模型" @keyup.enter="runSearch" />
            <el-button type="primary" :loading="searching" :disabled="!searchQuery.trim()" @click="runSearch">检索</el-button>
          </div>

          <div v-if="searchMeta" class="search-meta">{{ searchMeta }}</div>

          <el-empty v-if="!searchHits.length" description="暂无检索结果" :image-size="90" />
          <el-scrollbar v-else class="hits-scroll">
            <div v-for="(hit, index) in searchHits" :key="String(hit.id ?? index)" class="hit-item">
              <div class="hit-head">
                <div class="hit-title">{{ hit.title || `片段 ${index + 1}` }}</div>
                <div class="hit-head-right">
                  <el-tag type="info" effect="light">score: {{ formatScore(hit.score) }}</el-tag>
                  <el-tooltip content="查看详细信息" placement="top">
                    <el-button link class="hit-detail-btn" :icon="Document" @click="openHitDetail(hit)" />
                  </el-tooltip>
                </div>
              </div>
              <div class="hit-content">{{ extractContent(hit.payload) }}</div>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showHitDetailDialog" title="向量详情" width="640px" destroy-on-close>
      <div v-if="hitDetailItem" class="hit-detail-body">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="ID">{{ hitDetailItem.id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ hitDetailItem.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="相似度">{{ formatScore(hitDetailItem.score) }}</el-descriptions-item>
        </el-descriptions>
        <div class="hit-detail-section-title">Payload</div>
        <el-scrollbar class="hit-detail-scroll">
          <pre class="hit-detail-pre">{{ JSON.stringify(hitDetailItem.payload, null, 2) }}</pre>
        </el-scrollbar>
      </div>
    </el-dialog>

    <el-dialog v-model="showManageDialog" title="集合与向量管理" width="760px" destroy-on-close>
      <div class="manage-dialog-body">
        <div class="collection-exists-block">
          <div class="collection-exists-label">
            <span>已有集合</span>
            <el-button link :loading="listingCollections" class="collection-exists-refresh" :icon="Refresh" @click="refreshCollections" />
          </div>
          <div v-if="listingCollections" class="collection-exists-loading">加载中...</div>
          <el-empty v-else-if="!collectionList.length" description="暂无集合" :image-size="48" />
          <div v-else class="collection-exists-tags">
            <el-tag v-for="item in collectionList" :key="item" effect="plain" size="small">{{ item }}</el-tag>
          </div>
        </div>
        <el-select
          v-model="manageForm.collection"
          placeholder="请选择目标集合（删除集合/向量操作必选）"
          class="knowledge-field"
          filterable
          clearable
        >
          <el-option v-for="item in collectionList" :key="item" :label="item" :value="item" />
        </el-select>
        <div class="knowledge-hint">删除集合、删除向量、清空向量都必须先选择集合</div>
        <div class="search-row">
          <el-button type="danger" plain :loading="deletingCollection" :disabled="!hasValidSelectedCollection" @click="submitDeleteCollection">删除集合</el-button>
          <el-button type="danger" plain :loading="clearingVectors" :disabled="!hasValidSelectedCollection" @click="submitClearVectors">清空集合向量</el-button>
        </div>
        <div class="search-row">
          <el-input v-model="manageForm.singleId" placeholder="单个向量 ID" />
          <el-button type="danger" plain :loading="deletingSingleVector" :disabled="!hasValidSelectedCollection" @click="submitDeleteSingleVector">单个删除向量</el-button>
        </div>
        <el-input
          v-model="manageForm.batchIds"
          type="textarea"
          :rows="3"
          placeholder="批量向量 ID，逗号/空格/换行分隔"
          class="knowledge-field"
        />
        <el-button type="danger" plain :loading="deletingBatchVectors" :disabled="!hasValidSelectedCollection" @click="submitDeleteBatchVectors">批量删除向量</el-button>
        <div v-if="manageResult" class="collection-result">{{ manageResult }}</div>
      </div>
      <template #footer>
        <el-button @click="showManageDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/store/chat'
import {
  clearKnowledgeVectors,
  deleteKnowledgeCollection,
  deleteKnowledgeVector,
  deleteKnowledgeVectors,
  detectEmbeddingDimension,
  initKnowledgeCollection,
  listModelConfigOptions,
  listKnowledgeCollections,
  searchKnowledge
} from '@/api/chat'
import type { KnowledgeSearchResult, ModelConfigOptionItem } from '@/types/chat'

const router = useRouter()
const store = useChatStore()

const activeIngestTab = ref('text')
const initializingCollection = ref(false)
const detectingDimension = ref(false)
const collectionResult = ref('')
const collectionForm = ref({
  vectorSize: 0,
  distance: 'Cosine'
})
const indexingKnowledge = ref(false)
const indexingKnowledgeFile = ref(false)
const knowledgeResult = ref('')
const selectedFile = ref<File | null>(null)
const selectedFileName = ref('')
const knowledgeRecords = ref([] as Array<{ id: string; title: string; chunks: number; collection: string; time: string }>)
const knowledgeForm = ref({
  title: '',
  content: '',
  chunkSize: 500,
  chunkOverlap: 100,
  embeddingModel: 'bge-large-zh-v1.5'
})

const embeddingModelOptions = ref([
  { label: 'bge-large-zh-v1.5', value: 'bge-large-zh-v1.5' },
  { label: 'bge-m3', value: 'bge-m3' },
  { label: 'text-embedding-3-large', value: 'text-embedding-3-large' },
  { label: 'text-embedding-3-small', value: 'text-embedding-3-small' },
  { label: 'jina-embeddings-v2-base-zh', value: 'jina-embeddings-v2-base-zh' }
])

const searchQuery = ref('')
const searching = ref(false)
const searchHits = ref([] as Array<{ id?: string | number; title?: string; score?: number; payload?: Record<string, unknown> }>)
const searchMeta = ref('')
const showHitDetailDialog = ref(false)
const hitDetailItem = ref<{ id?: string | number; title?: string; score?: number; payload?: Record<string, unknown> } | null>(null)
const showManageDialog = ref(false)
const listingCollections = ref(false)
const deletingCollection = ref(false)
const deletingSingleVector = ref(false)
const deletingBatchVectors = ref(false)
const clearingVectors = ref(false)
const manageResult = ref('')
const collectionList = ref([] as string[])
const manageForm = ref({
  collection: '',
  singleId: '',
  batchIds: ''
})
const selectedCollection = computed(() => manageForm.value.collection.trim())
const hasValidSelectedCollection = computed(() => {
  const selected = selectedCollection.value
  return !!selected && collectionList.value.includes(selected)
})

onMounted(() => {
  void refreshCollections()
  void loadCollectionStatus()
  void loadEmbeddingModelOptions()
})

async function loadEmbeddingModelOptions() {
  try {
    const options = await listModelConfigOptions('EMBEDDING')
    if (!options.length) {
      return
    }
    embeddingModelOptions.value = options.map((item: ModelConfigOptionItem) => ({
      label: item.label || `${item.providerKey} / ${item.model}`,
      value: item.model
    }))
    if (!knowledgeForm.value.embeddingModel) {
      knowledgeForm.value.embeddingModel = embeddingModelOptions.value[0]?.value || ''
    }
  } catch {
    // 忽略加载失败，使用页面默认选项
  }
}

async function loadCollectionStatus() {
  try {
    const result = await initKnowledgeCollection({
      vectorSize: Number(collectionForm.value.vectorSize) > 0 ? Number(collectionForm.value.vectorSize) : undefined,
      distance: collectionForm.value.distance,
      embeddingModel: knowledgeForm.value.embeddingModel || undefined
    })
    if (Number(result.vectorSize || 0) > 0) {
      collectionForm.value.vectorSize = Number(result.vectorSize)
    }
    if (result.distance) {
      collectionForm.value.distance = String(result.distance)
    }
    const autoTag = result.autoDetected ? '（自动探测维度）' : ''
    const modelTag = result.embeddingModel ? `，model=${String(result.embeddingModel)}` : ''
    collectionResult.value = `集合状态：${String(result.status || '')}${autoTag}，size=${String(result.vectorSize || '')}，distance=${String(result.distance || '')}${modelTag}`
  } catch {
    // 静默失败，不影响页面正常使用
  }
}

function goChatPage() {
  void router.push('/')
}

function openManageDialog() {
  manageForm.value.collection = ''
  manageResult.value = ''
  showManageDialog.value = true
}

async function submitInitCollection() {
  initializingCollection.value = true
  collectionResult.value = ''
  try {
    const result = await initKnowledgeCollection({
      vectorSize: Number(collectionForm.value.vectorSize) > 0 ? Number(collectionForm.value.vectorSize) : undefined,
      distance: collectionForm.value.distance,
      embeddingModel: knowledgeForm.value.embeddingModel || undefined
    })
    if (Number(result.vectorSize || 0) > 0) {
      collectionForm.value.vectorSize = Number(result.vectorSize)
    }
    const autoTag = result.autoDetected ? '（自动探测维度）' : ''
    const modelTag = result.embeddingModel ? `，model=${String(result.embeddingModel)}` : ''
    collectionResult.value = `集合状态：${String(result.status || '')}${autoTag}，size=${String(result.vectorSize || '')}，distance=${String(result.distance || '')}${modelTag}`
    void refreshCollections()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '集合初始化失败')
  } finally {
    initializingCollection.value = false
  }
}

async function submitDetectDimension() {
  detectingDimension.value = true
  collectionResult.value = ''
  try {
    const result = await detectEmbeddingDimension(searchQuery.value.trim() || undefined, knowledgeForm.value.embeddingModel || undefined)
    const detected = Number(result.dimension || 0)
    if (detected > 0) {
      collectionForm.value.vectorSize = detected
    }
    collectionResult.value = `维度探测完成：model=${String(result.model || '')}，dimension=${String(result.dimension || '')}`
    ElMessage.success('已自动回填向量维度')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '维度探测失败')
  } finally {
    detectingDimension.value = false
  }
}

async function submitKnowledge() {
  const content = knowledgeForm.value.content.trim()
  if (!content) {
    return
  }
  indexingKnowledge.value = true
  knowledgeResult.value = ''
  try {
    const result = await store.ingestKnowledge({
      title: knowledgeForm.value.title.trim() || undefined,
      content,
      chunkSize: Number(knowledgeForm.value.chunkSize),
      chunkOverlap: Number(knowledgeForm.value.chunkOverlap),
      embeddingModel: knowledgeForm.value.embeddingModel || undefined
    })
    knowledgeResult.value = `入库完成：chunks=${String(result.chunks || 0)}，collection=${String(result.collection || '')}`
    knowledgeRecords.value.unshift({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title: knowledgeForm.value.title.trim(),
      chunks: Number(result.chunks || 0),
      collection: String(result.collection || ''),
      time: formatDateTime(new Date())
    })
    knowledgeRecords.value = knowledgeRecords.value.slice(0, 10)
    knowledgeForm.value.content = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识入库失败')
  } finally {
    indexingKnowledge.value = false
  }
}

function handleKnowledgeFileChange(file: { raw?: File }) {
  selectedFile.value = file.raw || null
  selectedFileName.value = file.raw?.name || ''
}

async function submitKnowledgeFile() {
  if (!selectedFile.value) {
    return
  }
  indexingKnowledgeFile.value = true
  knowledgeResult.value = ''
  try {
    const result = await store.ingestKnowledgeFile({
      file: selectedFile.value,
      title: knowledgeForm.value.title.trim() || undefined,
      chunkSize: Number(knowledgeForm.value.chunkSize),
      chunkOverlap: Number(knowledgeForm.value.chunkOverlap),
      embeddingModel: knowledgeForm.value.embeddingModel || undefined
    })
    knowledgeResult.value = `文件入库完成：chunks=${String(result.chunks || 0)}，collection=${String(result.collection || '')}`
    knowledgeRecords.value.unshift({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title: knowledgeForm.value.title.trim() || selectedFileName.value,
      chunks: Number(result.chunks || 0),
      collection: String(result.collection || ''),
      time: formatDateTime(new Date())
    })
    knowledgeRecords.value = knowledgeRecords.value.slice(0, 10)
    selectedFile.value = null
    selectedFileName.value = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件知识入库失败')
  } finally {
    indexingKnowledgeFile.value = false
  }
}

async function runSearch() {
  const query = searchQuery.value.trim()
  if (!query) {
    return
  }
  searching.value = true
  searchHits.value = []
  searchMeta.value = ''
  try {
    const result: KnowledgeSearchResult = await searchKnowledge(query)
    searchHits.value = result.hits || []
    searchMeta.value = `collection=${String(result.collection || '')} · provider=${String(result.provider || '')} · hits=${searchHits.value.length}`
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识检索失败')
  } finally {
    searching.value = false
  }
}

async function refreshCollections() {
  listingCollections.value = true
  manageResult.value = ''
  try {
    const result = await listKnowledgeCollections()
    collectionList.value = result.collections || []
    if (manageForm.value.collection && !collectionList.value.includes(manageForm.value.collection)) {
      manageForm.value.collection = ''
    }
    manageResult.value = `当前集合数量：${String(result.count || 0)}`
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '列出集合失败')
  } finally {
    listingCollections.value = false
  }
}

async function submitDeleteCollection() {
  const collection = selectedCollection.value
  if (!hasValidSelectedCollection.value) {
    ElMessage.warning('请从下拉列表中选择有效集合')
    return
  }
  deletingCollection.value = true
  manageResult.value = ''
  try {
    const result = await deleteKnowledgeCollection(collection)
    manageResult.value = `集合删除完成：collection=${String(result.collection || collection)}，status=${String(result.status || '')}`
    if (manageForm.value.collection === collection) {
      manageForm.value.collection = ''
    }
    await refreshCollections()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除集合失败')
  } finally {
    deletingCollection.value = false
  }
}

async function submitDeleteSingleVector() {
  const collection = selectedCollection.value
  if (!hasValidSelectedCollection.value) {
    ElMessage.warning('请从下拉列表中选择有效集合')
    return
  }
  const id = manageForm.value.singleId.trim()
  if (!id) {
    ElMessage.warning('请先输入向量 ID')
    return
  }
  deletingSingleVector.value = true
  manageResult.value = ''
  try {
    const result = await deleteKnowledgeVector(collection, id)
    manageResult.value = `单个向量删除完成：deleted=${String(result.deleted || 0)}，collection=${String(result.collection || '')}`
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '单个删除向量失败')
  } finally {
    deletingSingleVector.value = false
  }
}

async function submitDeleteBatchVectors() {
  const collection = selectedCollection.value
  if (!hasValidSelectedCollection.value) {
    ElMessage.warning('请从下拉列表中选择有效集合')
    return
  }
  const ids = manageForm.value.batchIds
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
  if (!ids.length) {
    ElMessage.warning('请先输入批量向量 ID')
    return
  }
  deletingBatchVectors.value = true
  manageResult.value = ''
  try {
    const result = await deleteKnowledgeVectors(collection, ids)
    manageResult.value = `批量删除完成：deleted=${String(result.deleted || 0)}，collection=${String(result.collection || '')}`
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除向量失败')
  } finally {
    deletingBatchVectors.value = false
  }
}

async function submitClearVectors() {
  const collection = selectedCollection.value
  if (!hasValidSelectedCollection.value) {
    ElMessage.warning('请从下拉列表中选择有效集合')
    return
  }
  clearingVectors.value = true
  manageResult.value = ''
  try {
    const result = await clearKnowledgeVectors(collection)
    manageResult.value = `向量清空完成：deleted=${String(result.deleted || 0)}，collection=${String(result.collection || '')}`
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清空向量失败')
  } finally {
    clearingVectors.value = false
  }
}

function extractContent(payload?: Record<string, unknown>) {
  if (!payload) {
    return ''
  }
  const content = payload.content
  if (typeof content === 'string') {
    return content
  }
  return JSON.stringify(payload)
}

function formatScore(score?: number) {
  if (typeof score !== 'number' || Number.isNaN(score)) {
    return '-'
  }
  return score.toFixed(4)
}

function openHitDetail(hit: { id?: string | number; title?: string; score?: number; payload?: Record<string, unknown> }) {
  hitDetailItem.value = hit
  showHitDetailDialog.value = true
}

function formatDateTime(date: Date) {
  const yyyy = String(date.getFullYear())
  const MM = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${yyyy}-${MM}-${dd} ${hh}:${mm}:${ss}`
}
</script>

<style scoped>
.knowledge-page {
  height: 100dvh;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f5f7fa 0%, #eef3ff 100%);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.page-header p {
  margin: 0;
  color: #606266;
}

.knowledge-layout {
  flex: 1;
  min-height: 0;
}

.panel-col {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.side-card,
.result-card {
  border-radius: 16px;
  flex: 1;
  min-height: 0;
}

.side-card :deep(.el-card__body),
.result-card :deep(.el-card__body) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  font-weight: 600;
}

.knowledge-field {
  margin-bottom: 6px;
}

.knowledge-tabs :deep(.el-tabs__content) {
  padding-top: 4px;
}

.knowledge-tabs :deep(textarea) {
  max-height: 160px;
  resize: vertical;
}

.collection-init-block {
  padding: 8px 10px;
  border: 1px dashed #dcdfe6;
  border-radius: 10px;
  background: #fcfdff;
}

.collection-init-title {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}

.collection-result {
  margin-top: 8px;
  color: #67c23a;
  font-size: 12px;
}

.collection-exists-block {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f7f9fc;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.collection-exists-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.collection-exists-refresh {
  font-size: 13px;
  padding: 0;
  height: auto;
}

.collection-exists-loading {
  font-size: 12px;
  color: #909399;
}

.collection-exists-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.file-name {
  margin-bottom: 10px;
  color: #606266;
  font-size: 12px;
}

.knowledge-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.knowledge-hint {
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}

.collection-tag {
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.collection-refresh-btn {
  width: 24px;
  height: 24px;
  padding: 0;
}

.collection-refresh-btn :deep(.el-icon) {
  font-size: 12px;
}

.manage-dialog-body {
  padding-top: 4px;
}

.success-text {
  margin-top: 10px;
  color: #67c23a;
  word-break: break-all;
}

.records-wrap {
  margin-top: 12px;
  border-top: 1px dashed #dcdfe6;
  padding-top: 10px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.records-title {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}

.records-scroll {
  flex: 1;
  min-height: 0;
}

.record-item {
  padding: 8px;
  border-radius: 8px;
  background: #f7f9fc;
  margin-bottom: 8px;
}

.record-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.record-title {
  font-size: 12px;
  color: #303133;
  font-weight: 500;
}

.record-time {
  font-size: 11px;
  color: #909399;
}

.record-sub {
  margin-top: 4px;
  font-size: 11px;
  color: #606266;
}

.search-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.search-meta {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}

.hits-scroll {
  flex: 1;
  min-height: 0;
}

.hit-item {
  padding: 12px;
  border-radius: 10px;
  background: #f7f9fc;
  margin-bottom: 10px;
}

.hit-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hit-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.hit-detail-btn {
  color: #909399;
  font-size: 16px;
  padding: 0;
}

.hit-detail-btn:hover {
  color: #409eff;
}

.hit-detail-body {
  padding-top: 4px;
}

.hit-detail-section-title {
  margin: 14px 0 6px;
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.hit-detail-scroll {
  max-height: 320px;
}

.hit-detail-pre {
  margin: 0;
  padding: 12px;
  background: #f4f5f7;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}

.hit-title {
  color: #303133;
  font-weight: 600;
}

.hit-content {
  margin-top: 8px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
