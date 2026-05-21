<template>
  <div class="graph-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1>图谱管理</h1>
        <p>同步完成文本/文件抽取、预览确认与实体分页查看。</p>
      </div>
      <el-space>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <!-- 主体两栏 -->
    <div class="graph-body">
      <!-- 左栏：抽取表单，不出现滚动条 -->
      <div class="left-col">
        <el-card shadow="never" class="full-card">
          <template #header><span class="panel-header">抽取预览</span></template>

          <div class="graph-field">
            <div class="field-label">图谱空间</div>
            <div class="action-row">
              <el-select v-model="form.graphSpace" filterable allow-create default-first-option style="flex:1">
                <el-option v-for="item in spaces.items||[]" :key="item.graphSpace" :label="item.graphSpace" :value="item.graphSpace" />
              </el-select>
              <el-button :loading="creatingSpace" @click="createSpace">创建</el-button>
              <el-button type="danger" plain :loading="deletingSpace" @click="removeSpace">删除</el-button>
            </div>
          </div>

          <el-input v-model="form.title" placeholder="标题（可选）" class="graph-field" />

          <el-row :gutter="12" class="graph-field">
            <el-col :span="12">
              <el-select v-model="form.extractMode" style="width:100%">
                <el-option label="LLM_ONLY" value="LLM_ONLY" />
                <el-option label="LLM_RULES" value="LLM_RULES" />
                <el-option label="LLM_HUMAN" value="LLM_HUMAN" />
              </el-select>
            </el-col>
            <el-col :span="12">
              <el-select v-model="form.importMode" style="width:100%">
                <el-option label="MERGE_APPEND" value="MERGE_APPEND" />
                <el-option label="REPLACE_SPACE" value="REPLACE_SPACE" />
              </el-select>
            </el-col>
          </el-row>

          <el-select v-model="form.llmModel" filterable allow-create default-first-option class="graph-field" placeholder="请选择或输入图谱抽取模型">
            <el-option v-for="item in graphModelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>

          <el-tabs v-model="activeTab" class="graph-field">
            <el-tab-pane label="文本抽取" name="text">
              <el-input v-model="form.inputText" type="textarea" :rows="4" placeholder="请输入待抽取文本" />
              <el-button class="graph-action" type="primary" :loading="extractingText" :disabled="!form.inputText.trim()" @click="submitText">抽取预览</el-button>
            </el-tab-pane>
            <el-tab-pane label="文件抽取" name="file">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleFileChange">
                <el-button plain>选择文件</el-button>
              </el-upload>
              <div v-if="selectedFileName" class="file-name">已选：{{ selectedFileName }}</div>
              <el-button class="graph-action" type="primary" plain :loading="extractingFile" :disabled="!selectedFile" @click="submitFile">文件抽取预览</el-button>
            </el-tab-pane>
          </el-tabs>

          <el-input v-model="form.promptOverride" type="textarea" :rows="3" placeholder="Prompt Override（可选，灰度使用）" class="graph-field" />

          <div class="action-row graph-field">
            <span class="field-label" style="flex-shrink:0">maxTokens</span>
            <el-input-number v-model="form.maxTokens" :min="256" :max="4096" :step="128" controls-position="right" style="flex:1" />
          </div>
          <div class="graph-hint">首版只支持同步预览与人工确认导入。</div>
        </el-card>
      </div>

      <!-- 右栏：三卡片各占约 1/3 高度，各自独立滚动 -->
      <div class="right-col">
        <!-- 预览与导入 -->
        <div class="right-panel">
          <el-card shadow="never" class="full-card">
            <template #header>
              <div class="panel-header-row">
                <span class="panel-header">预览与导入</span>
                <div class="action-row" style="margin:0">
                  <el-button
                    v-if="preview"
                    type="primary"
                    size="small"
                    :loading="importing"
                    :disabled="importing || importResult?.previewId === preview?.previewId"
                    @click="confirmImport"
                  >
                    确认入图
                  </el-button>
                  <el-button v-if="preview" size="small" plain @click="loadEntities">刷新列表</el-button>
                  <el-button v-if="preview" size="small" plain @click="relayoutCanvas">重布局</el-button>
                  <el-button size="small" plain @click="openGraphDialog">图谱展示（D3）</el-button>
                  <el-button
                    v-if="preview"
                    size="small"
                    plain
                    :disabled="!selectedCanvasNode"
                    :loading="expandingNode"
                    @click="expandOneHop"
                  >
                    扩展一跳
                  </el-button>
                  <el-button v-if="importResult?.importBatchId" type="danger" size="small" plain :loading="rollingBackBatch" @click="rollbackImportBatch">回滚</el-button>
                </div>
              </div>
            </template>

            <el-empty v-if="!preview" description="暂无预览结果" :image-size="72" />
            <template v-else>
              <el-descriptions :column="4" border size="small" class="graph-field">
                <el-descriptions-item label="graphSpace">{{ preview.graphSpace }}</el-descriptions-item>
                <el-descriptions-item label="实体数">{{ preview.stats?.entityCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="关系数">{{ preview.stats?.relationshipCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag v-if="importResult" type="success" size="small">已导入</el-tag>
                  <el-tag v-else size="small">待确认</el-tag>
                </el-descriptions-item>
              </el-descriptions>
              <el-alert v-for="w in preview.warnings||[]" :key="w" :title="w" type="warning" :closable="false" class="graph-field" />
              <div v-if="importResult" class="success-text graph-field">
                batch={{ importResult.importBatchId }}，entities={{ importResult.importedEntities||0 }}，relationships={{ importResult.importedRelationships||0 }}
              </div>

              <el-row :gutter="12">
                <el-col :span="12">
                  <div class="sub-title">实体预览</div>
                  <el-table :data="preview.entities||[]" size="small" class="inner-table">
                    <el-table-column prop="entityName" label="实体名" min-width="120" />
                    <el-table-column prop="entityType" label="类型" width="90" />
                    <el-table-column label="详情" width="60" fixed="right">
                      <template #default="{ row }">
                        <el-button link size="small" @click="showEntityDetail(row)">详情</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-col>
                <el-col :span="12">
                  <div class="sub-title">关系预览</div>
                  <el-table :data="preview.relationships||[]" size="small" class="inner-table">
                    <el-table-column prop="sourceEntity" label="源" min-width="100" />
                    <el-table-column prop="targetEntity" label="目标" min-width="100" />
                    <el-table-column label="详情" width="60" fixed="right">
                      <template #default="{ row }">
                        <el-button link size="small" @click="showRelDetail(row)">详情</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-col>
              </el-row>
            </template>
          </el-card>
        </div>

        <!-- 实体列表 -->
        <div class="right-panel">
          <el-card shadow="never" class="full-card">
            <template #header>
              <div class="panel-header-row">
                <span class="panel-header">实体列表</span>
                <div class="action-row" style="margin:0">
                  <el-input v-model="entityQuery.keyword" placeholder="关键字" clearable size="small" style="width:140px" />
                  <el-button size="small" :loading="loadingEntities" @click="loadEntities">查询</el-button>
                  <el-button size="small" type="danger" plain :disabled="!selectedEntityNames.length" @click="removeEntities">删除选中</el-button>
                </div>
              </div>
            </template>
            <el-table :data="entityPage.items||[]" size="small" class="inner-table" @selection-change="onEntitySelectionChange">
              <el-table-column type="selection" width="40" />
              <el-table-column prop="entityName" label="实体名" min-width="140" />
              <el-table-column prop="entityType" label="类型" width="90" />
              <el-table-column prop="updatedAt" label="更新时间" width="160" />
              <el-table-column label="详情" width="60" fixed="right">
                <template #default="{ row }">
                  <el-button link size="small" @click="showEntityDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <!-- 关系列表 -->
        <div class="right-panel">
          <el-card shadow="never" class="full-card">
            <template #header>
              <div class="panel-header-row">
                <span class="panel-header">关系列表</span>
                <div class="action-row" style="margin:0">
                  <el-input v-model="relationshipQuery.keyword" placeholder="关键字" clearable size="small" style="width:140px" />
                  <el-button size="small" :loading="loadingRelationships" @click="loadRelationships">查询</el-button>
                  <el-button size="small" type="danger" plain :disabled="!selectedRelationshipIds.length" @click="removeRelationships">删除选中</el-button>
                </div>
              </div>
            </template>
            <el-table :data="relationshipPage.items||[]" size="small" class="inner-table" @selection-change="onRelationshipSelectionChange">
              <el-table-column type="selection" width="40" />
              <el-table-column prop="sourceEntity" label="源实体" min-width="120" />
              <el-table-column prop="targetEntity" label="目标实体" min-width="120" />
              <el-table-column prop="relationshipStrength" label="强度" width="60" />
              <el-table-column prop="updatedAt" label="更新时间" width="160" />
              <el-table-column label="详情" width="60" fixed="right">
                <template #default="{ row }">
                  <el-button link size="small" @click="showRelDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 实体详情弹窗 -->
    <el-dialog v-model="entityDetailVisible" title="实体详情" width="480px" draggable>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="实体名">{{ entityDetail?.entityName }}</el-descriptions-item>
        <el-descriptions-item label="规范名">{{ entityDetail?.normalizedName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ entityDetail?.entityType }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ entityDetail?.entityDescription }}</el-descriptions-item>
        <el-descriptions-item label="图谱空间">{{ entityDetail?.graphSpace }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ entityDetail?.updatedAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 关系详情弹窗 -->
    <el-dialog v-model="relDetailVisible" title="关系详情" width="480px" draggable>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="源实体">{{ relDetail?.sourceEntity }}</el-descriptions-item>
        <el-descriptions-item label="目标实体">{{ relDetail?.targetEntity }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ relDetail?.relationshipDescription }}</el-descriptions-item>
        <el-descriptions-item label="强度">{{ relDetail?.relationshipStrength }}</el-descriptions-item>
        <el-descriptions-item label="图谱空间">{{ relDetail?.graphSpace }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ relDetail?.updatedAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="graphDialogVisible" title="图谱展示（D3）" fullscreen draggable append-to-body :destroy-on-close="false" class="graph-fullscreen-dialog">
      <div class="graph-dialog-actions">
        <el-tag size="small" type="info">当前空间：{{ form.graphSpace || 'default' }}</el-tag>
        <el-tag v-if="selectedCanvasNode" size="small" type="warning">中心节点：{{ selectedCanvasNode }}</el-tag>
        <el-select
          v-model="canvasTypeFilter"
          placeholder="实体类型筛选"
          clearable
          filterable
          size="small"
          style="width: 180px"
          @clear="clearCanvasTypeFilter"
          @change="onCanvasTypeFilterChange"
        >
          <el-option
            v-for="item in graphCanvasTypeOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
        <!-- 节点搜索框 -->
        <el-autocomplete
          v-model="canvasSearchText"
          :fetch-suggestions="fetchNodeSuggestions"
          placeholder="搜索节点…"
          size="small"
          clearable
          style="width: 220px"
          value-key="label"
          highlight-first-item
          @select="onNodeSuggestionSelect"
          @clear="canvasSearchText = ''"
        >
          <template #prefix>
            <svg width="13" height="13" viewBox="0 0 14 14" fill="none" style="vertical-align:middle">
              <circle cx="6" cy="6" r="4.5" stroke="#94a3b8" stroke-width="1.5"/>
              <path d="M10 10l2.5 2.5" stroke="#94a3b8" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </template>
        </el-autocomplete>
        <el-button size="small" plain @click="relayoutCanvas">重布局</el-button>
        <el-button size="small" plain :disabled="!selectedCanvasNode" :loading="expandingNode" @click="expandOneHop">扩展一跳</el-button>
      </div>
      <!-- canvas + 右侧属性面板 -->
      <div class="graph-canvas-wrap">
        <GraphCanvasD3
          ref="graphCanvasRef"
          :height="canvasHeight"
          :nodes="graphCanvasNodes"
          :edges="graphCanvasEdges"
          :highlight-keyword="canvasSearchText || graphCanvasKeyword"
          @node-click="onCanvasNodeClick"
          @edge-click="onCanvasEdgeClick"
          @node-expand="onCanvasNodeExpand"
        />
        <!-- 右侧属性面板（Neo4j 风格） -->
        <div v-show="nodeDetailPanelVisible" class="node-detail-panel" :class="{ 'panel-visible': nodeDetailPanelVisible }">
            <div class="ndp-header">
              <div class="ndp-label-row">
                <span
                  class="ndp-dot"
                  :style="{ background: nodeDetailPanelIsEdge ? '#8899aa' : '#4C8EDA' }"
                />
                <span class="ndp-kind-tag">
                  {{ nodeDetailPanelIsEdge ? 'Relationship' : (nodeDetailPanelItem?.entityType || 'Entity') }}
                </span>
              </div>
              <el-button class="ndp-close-btn" link @click="nodeDetailPanelVisible = false">
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                  <path d="M1 1l12 12M13 1L1 13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </el-button>
            </div>

            <div class="ndp-node-title">
              {{ nodeDetailPanelIsEdge
                ? `${(nodeDetailPanelItem as any)?.sourceEntity} → ${(nodeDetailPanelItem as any)?.targetEntity}`
                : (nodeDetailPanelItem as any)?.entityName }}
            </div>

            <div v-if="!nodeDetailPanelIsEdge" class="ndp-node-type-line">
              节点类型：{{ (nodeDetailPanelItem as any)?.entityType || '未标注' }}
            </div>

            <div class="ndp-section-title">Node properties</div>
            <div class="ndp-props-list">
              <div
                v-for="[k, v] in nodeDetailEntries"
                :key="k"
                class="ndp-prop-row"
              >
                <div class="ndp-prop-key">{{ k }}</div>
                <div class="ndp-prop-val" :title="String(v ?? '')">{{ v }}</div>
              </div>
              <div v-if="!nodeDetailEntries.length" class="ndp-empty">暂无属性</div>
            </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import GraphCanvasD3 from '@/components/graph/GraphCanvasD3.vue'
import { listModelConfigOptions } from '@/api/chat'
import {
  createGraphSpace,
  deleteGraphEntities,
  deleteGraphImportBatch,
  deleteGraphRelationships,
  deleteGraphSpace,
  extractGraphFile,
  extractGraphText,
  importGraphPreview,
  listGraphEntityTypes,
  listGraphSpaces,
  pageGraphEntities,
  pageGraphRelationships,
  queryGraphOneHop
} from '@/api/graph'
import type {
  GraphEntityItem,
  GraphEntityPageResponse,
  GraphEntityTypeListResponse,
  GraphExtractPreviewResponse,
  GraphImportResultResponse,
  GraphRelationshipItem,
  GraphRelationshipPageResponse,
  GraphSpaceListResponse
} from '@/types/graph'

const router = useRouter()

const activeTab = ref('text')
const extractingText = ref(false)
const extractingFile = ref(false)
const importing = ref(false)
const rollingBackBatch = ref(false)
const loadingEntities = ref(false)
const loadingRelationships = ref(false)
const creatingSpace = ref(false)
const deletingSpace = ref(false)
const selectedFile = ref<File | null>(null)
const selectedFileName = ref('')
const preview = ref<GraphExtractPreviewResponse | null>(null)
const importResult = ref<GraphImportResultResponse | null>(null)
const entityPage = ref<GraphEntityPageResponse>({ graphSpace: 'default', items: [] })
const relationshipPage = ref<GraphRelationshipPageResponse>({ graphSpace: 'default', items: [] })
const spaces = ref<GraphSpaceListResponse>({ defaultSpace: 'default', items: [] })
const selectedEntityNames = ref<string[]>([])
const selectedRelationshipIds = ref<string[]>([])
const graphEntityBuffer = ref<GraphEntityItem[]>([])
const graphRelationshipBuffer = ref<GraphRelationshipItem[]>([])
const graphEntityTypeList = ref<GraphEntityTypeListResponse>({ graphSpace: 'default', items: [] })
const selectedCanvasNode = ref('')
const expandingNode = ref(false)
const graphDialogVisible = ref(false)
// 全屏弹框内 canvas 高度 = 视口高度 - header(54) - actions(约40) - padding(24) - actions margin(10)
const canvasHeight = computed(() => Math.max(300, window.innerHeight - 54 - 40 - 24 - 10))

// 列表详情弹窗（保留，供表格行点击使用）
const entityDetailVisible = ref(false)
const entityDetail = ref<GraphEntityItem | null>(null)
const relDetailVisible = ref(false)
const relDetail = ref<Record<string, unknown> | null>(null)

function showEntityDetail(row: GraphEntityItem) {
  entityDetail.value = row
  entityDetailVisible.value = true
}

function showRelDetail(row: Record<string, unknown>) {
  relDetail.value = row
  relDetailVisible.value = true
}

// 图谱右侧属性面板（Neo4j 风格）
const nodeDetailPanelVisible = ref(false)
const nodeDetailPanelItem = ref<Record<string, unknown> | null>(null)
const nodeDetailPanelIsEdge = ref(false)

// 图谱画布搜索
const graphCanvasRef = ref<InstanceType<typeof GraphCanvasD3> | null>(null)
const canvasSearchText = ref('')
const canvasTypeFilter = ref<string | undefined>('')

function normalizeEntityType(value?: string | null) {
  return String(value || '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeEntityKey(value?: string | null) {
  return String(value || '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .trim()
    .toLowerCase()
}

function normalizeTypeKey(value?: string | null) {
  return normalizeEntityType(value)
    .toLowerCase()
    .replace(/[\s\-_/|,，、;；:：()（）【】\[\]{}]+/g, '')
}

function extractTypeTokens(value?: string | null) {
  const raw = normalizeEntityType(value)
  if (!raw) return [] as string[]
  const tokenSet = new Set<string>([raw])
  raw.split(/[\s,，、/|;；:：]+/).map(s => s.trim()).filter(Boolean).forEach(s => tokenSet.add(s))
  const keywordHints = ['人物', '地点', '组织', '时间', '事件', '物品']
  for (const key of keywordHints) {
    if (raw.includes(key)) tokenSet.add(key)
  }
  return Array.from(tokenSet)
}

function isTypeMatched(entityType?: string | null, filter?: string | null) {
  const f = normalizeTypeKey(filter)
  if (!f) return true
  const t = normalizeTypeKey(entityType)
  if (!t) return false
  return t.includes(f) || f.includes(t)
}

function clearCanvasTypeFilter() {
  canvasTypeFilter.value = ''
  void onCanvasTypeFilterChange()
}

const graphCanvasTypeOptions = computed(() => {
  const typeSet = new Set<string>()
  // 0) 来自后端聚合接口（主来源）
  ;(graphEntityTypeList.value.items || []).filter(Boolean).forEach(item => {
    extractTypeTokens(item).forEach(t => typeSet.add(t))
  })
  // 1) 当前输入/已选值（防止刷新后不在下拉里）
  const current = normalizeEntityType(canvasTypeFilter.value)
  if (current) {
    typeSet.add(current)
  }
  return Array.from(typeSet).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

async function loadEntityTypes() {
  try {
    graphEntityTypeList.value = await listGraphEntityTypes({
      graphSpace: form.graphSpace.trim() || 'default'
    })
  } catch (error) {
    // 保留前端本地兜底，不中断主流程
    console.warn('[GraphPage] listGraphEntityTypes failed, fallback to local options', error)
  }
}

function fetchNodeSuggestions(queryStr: string, cb: (results: { value: string; label: string }[]) => void) {
  const q = queryStr.trim().toLowerCase()
  const allNodes = graphCanvasNodes.value
  const results = (q
    ? allNodes.filter(n => n.label.toLowerCase().includes(q))
    : allNodes.slice(0, 50)
  ).map(n => ({ value: n.id, label: n.label }))
  cb(results)
}

function onNodeSuggestionSelect(item: { value: string; label: string }) {
  canvasSearchText.value = item.label
  graphCanvasRef.value?.locateNode(item.value)
}

/** 过滤空值，将所有属性 key-value 展开 */
const nodeDetailEntries = computed(() => {
  if (!nodeDetailPanelItem.value) return []
  return Object.entries(nodeDetailPanelItem.value)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => [k, typeof v === 'object' ? JSON.stringify(v) : v] as [string, unknown])
})

/**
 * 图谱筛选实体池：合并 buffer 与当前分页实体，避免“面板里有类型但筛选不到”
 */
const graphEntityPool = computed(() => {
  const map = new Map<string, GraphEntityItem>()
  ;(graphEntityBuffer.value || []).forEach(item => {
    if (item?.entityName) {
      map.set(item.entityName, item)
    }
  })
  ;(entityPage.value.items || []).forEach(item => {
    if (!item?.entityName) {
      return
    }
    const prev = map.get(item.entityName)
    map.set(item.entityName, {
      ...(item as GraphEntityItem),
      ...(prev as GraphEntityItem | undefined)
    })
  })
  return Array.from(map.values())
})

const graphCanvasEdges = computed(() => {
  const type = normalizeEntityType(canvasTypeFilter.value)
  if (!type) {
    return graphRelationshipBuffer.value
      .map(item => ({
        id: item.relationshipId,
        source: item.sourceEntity,
        target: item.targetEntity,
        label: item.relationshipDescription,
        strength: item.relationshipStrength
      }))
  }

  const selectedTypeNodeIds = new Set(
    graphEntityPool.value
      .filter(item => isTypeMatched(item.entityType, type))
      .map(item => item.entityName)
  )

  return graphRelationshipBuffer.value
    .filter(item => selectedTypeNodeIds.has(item.sourceEntity) || selectedTypeNodeIds.has(item.targetEntity))
    .map(item => ({
      id: item.relationshipId,
      source: item.sourceEntity,
      target: item.targetEntity,
      label: item.relationshipDescription,
      strength: item.relationshipStrength
    }))
})

const graphCanvasNodes = computed(() => {
  const type = normalizeEntityType(canvasTypeFilter.value)
  if (!type) {
    return graphEntityPool.value.map(item => ({
      id: item.entityName,
      label: item.entityName,
      type: item.entityType
    }))
  }

  const relatedNodeIds = new Set<string>()
  graphCanvasEdges.value.forEach(item => {
    relatedNodeIds.add(item.source)
    relatedNodeIds.add(item.target)
  })

  return graphEntityPool.value
    .filter(item => isTypeMatched(item.entityType, type) || relatedNodeIds.has(item.entityName))
    .map(item => ({
      id: item.entityName,
      label: item.entityName,
      type: item.entityType
    }))
})

const graphCanvasKeyword = computed(() => {
  const keyword = entityQuery.keyword.trim() || relationshipQuery.keyword.trim()
  return keyword || ''
})

function relayoutCanvas() {
  graphCanvasRef.value?.relayout()
}

async function loadGraphCanvasDataByType() {
  const graphSpace = form.graphSpace.trim() || 'default'
  const entityType = normalizeEntityType(canvasTypeFilter.value)
  const [entityResp, relationshipResp] = await Promise.all([
    pageGraphEntities({
      graphSpace,
      page: 1,
      pageSize: 500,
      entityType: entityType || undefined
    }),
    pageGraphRelationships({
      graphSpace,
      page: 1,
      pageSize: 1000,
      entityType: entityType || undefined
    })
  ])
  graphEntityBuffer.value = entityResp.items || []
  graphRelationshipBuffer.value = relationshipResp.items || []
}

function onCanvasTypeFilterChange() {
  // 将整个执行体推入宏任务，完全脱离 el-select @change 的微任务链，
  // 避免 Vue patch 在 ElDialog / BaseTransition 内部遇到 null DOM（insertBefore/processCommentNode）
  setTimeout(async () => {
    if (!graphDialogVisible.value) return
    try {
      await loadGraphCanvasDataByType()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '图谱筛选加载失败')
    }
    relayoutCanvas()
  }, 0)
}

async function openGraphDialog() {
  try {
    await loadGraphCanvasDataByType()
    await loadEntityTypes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图谱数据加载失败')
  } finally {
    graphDialogVisible.value = true
    // 等 el-dialog 动画（~300ms）渲染完成后再重绘，确保 canvas 能量到真实宽高
    setTimeout(() => relayoutCanvas(), 350)
  }
}

function mergeEntities(base: GraphEntityItem[], incoming: GraphEntityItem[]) {
  const map = new Map<string, GraphEntityItem>()
  base.forEach(item => map.set(item.normalizedName || item.entityName, item))
  incoming.forEach(item => map.set(item.normalizedName || item.entityName, item))
  return Array.from(map.values())
}

function mergeRelationships(base: GraphRelationshipItem[], incoming: GraphRelationshipItem[]) {
  const map = new Map<string, GraphRelationshipItem>()
  base.forEach(item => map.set(item.relationshipId, item))
  incoming.forEach(item => map.set(item.relationshipId, item))
  return Array.from(map.values())
}

async function expandOneHop() {
  const centerNode = selectedCanvasNode.value.trim()
  if (!centerNode) {
    ElMessage.warning('请先点击图中的一个节点')
    return
  }
  expandingNode.value = true
  try {
    const oneHop = await queryGraphOneHop({
      graphSpace: form.graphSpace.trim() || 'default',
      centerEntity: centerNode,
      limitNodes: 120,
      limitEdges: 240
    })
    // 将响应式 buffer 更新推迟到下一个宏任务，彻底脱离当前事件链的微任务调度，
    // 避免 Vue patch 在 ElDialog / BaseTransition 内部碰到 null component instance
    setTimeout(() => {
      graphEntityBuffer.value = mergeEntities(graphEntityBuffer.value, oneHop.nodes || [])
      graphRelationshipBuffer.value = mergeRelationships(graphRelationshipBuffer.value, oneHop.edges || [])
    }, 0)
    ElMessage.success(`已扩展节点「${centerNode}」的一跳邻居`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '扩展一跳失败')
  } finally {
    expandingNode.value = false
  }
}

function onCanvasNodeClick(nodeId: string) {
  selectedCanvasNode.value = nodeId
  const key = normalizeEntityKey(nodeId)

  // 优先从合并实体池按 entityName / normalizedName 双通道匹配
  const poolItem = graphEntityPool.value.find(i =>
    normalizeEntityKey(i.entityName) === key || normalizeEntityKey(i.normalizedName) === key
  )

  // 再从预览实体兜底（有时 buffer/page 尚未覆盖，但预览里已有 entityType）
  const previewItem = (preview.value?.entities || []).find(i =>
    normalizeEntityKey(i.entityName) === key || normalizeEntityKey(i.normalizedName) === key
  )

  const canvasNode = graphCanvasNodes.value.find(i => i.id === nodeId)

  const previewAsEntity = previewItem
    ? {
      entityName: previewItem.entityName,
      normalizedName: previewItem.normalizedName,
      entityType: previewItem.entityType,
      entityDescription: previewItem.entityDescription
    }
    : {}

  nodeDetailPanelItem.value = {
    entityName: nodeId,
    entityType: normalizeEntityType(poolItem?.entityType || previewItem?.entityType || canvasNode?.type || ''),
    ...(previewAsEntity as Record<string, unknown>),
    ...((poolItem as unknown as Record<string, unknown>) || {})
  }
  nodeDetailPanelIsEdge.value = false
  nodeDetailPanelVisible.value = true
}

async function onCanvasNodeExpand(nodeId: string) {
  selectedCanvasNode.value = nodeId
  // 延后到下一轮事件循环，避免与 D3 节点点击/环形按钮点击同帧更新冲突
  setTimeout(() => {
    void expandOneHop()
  }, 0)
}

function onCanvasEdgeClick(edgeId: string) {
  const rel = (graphRelationshipBuffer.value).find(item => item.relationshipId === edgeId)
    || (relationshipPage.value.items || []).find(item => item.relationshipId === edgeId)
  if (rel) {
    nodeDetailPanelItem.value = rel as unknown as Record<string, unknown>
    nodeDetailPanelIsEdge.value = true
    nodeDetailPanelVisible.value = true
  }
}

const graphModelOptions = ref<Array<{ label: string; value: string }>>([
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'Qwen Max', value: 'qwen-max' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'GPT-4o Mini', value: 'gpt-4o-mini' }
])

const form = reactive({
  graphSpace: 'default',
  title: '',
  inputText: '',
  llmModel: 'qwen-plus',
  extractMode: 'LLM_RULES',
  importMode: 'MERGE_APPEND',
  promptOverride: '',
  maxTokens: 2048
})

const entityQuery = reactive({
  keyword: ''
})

const relationshipQuery = reactive({
  keyword: ''
})

onMounted(async () => {
  await loadGraphModelOptions()
  await loadSpaces()
  await loadEntities()
  await loadRelationships()
  await loadEntityTypes()
})

watch(() => form.graphSpace, () => {
  void loadEntities()
  void loadRelationships()
  void loadEntityTypes()
})

async function loadGraphModelOptions() {
  try {
    const options = await listModelConfigOptions('CHAT')
    if (!options.length) {
      return
    }
    graphModelOptions.value = options.map(item => ({
      label: item.label || `${item.providerKey} / ${item.model}`,
      value: item.model
    }))
    if (!form.llmModel) {
      form.llmModel = graphModelOptions.value[0]?.value || ''
    }
  } catch {
    // 回退内置模型选项
  }
}

function goChatPage() {
  void router.push('/')
}

function handleFileChange(file: { raw?: File }) {
  selectedFile.value = file.raw || null
  selectedFileName.value = file.raw?.name || ''
}

function onEntitySelectionChange(rows: GraphEntityItem[]) {
  selectedEntityNames.value = rows.map(item => item.normalizedName).filter(Boolean)
}

function onRelationshipSelectionChange(rows: GraphRelationshipItem[]) {
  selectedRelationshipIds.value = rows.map(item => item.relationshipId).filter(Boolean)
}

async function loadSpaces() {
  try {
    spaces.value = await listGraphSpaces()
    if (!form.graphSpace && spaces.value.defaultSpace) {
      form.graphSpace = spaces.value.defaultSpace
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图谱空间加载失败')
  }
}

async function createSpace() {
  const graphSpace = form.graphSpace.trim()
  if (!graphSpace) {
    ElMessage.warning('请先输入图谱空间名称')
    return
  }
  creatingSpace.value = true
  try {
    await createGraphSpace({ graphSpace })
    ElMessage.success('图谱空间创建成功')
    await loadSpaces()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图谱空间创建失败')
  } finally {
    creatingSpace.value = false
  }
}

async function removeSpace() {
  const graphSpace = form.graphSpace.trim()
  if (!graphSpace) {
    ElMessage.warning('请先选择图谱空间')
    return
  }
  deletingSpace.value = true
  try {
    await deleteGraphSpace(graphSpace)
    ElMessage.success('图谱空间删除成功')
    preview.value = null
    importResult.value = null
    entityPage.value = { graphSpace: 'default', items: [] }
    relationshipPage.value = { graphSpace: 'default', items: [] }
    await loadSpaces()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图谱空间删除失败')
  } finally {
    deletingSpace.value = false
  }
}

async function submitText() {
  extractingText.value = true
  importResult.value = null
  try {
    preview.value = await extractGraphText({
      graphSpace: form.graphSpace.trim() || undefined,
      title: form.title.trim() || undefined,
      inputText: form.inputText.trim(),
      llmModel: form.llmModel.trim() || undefined,
      extractMode: form.extractMode,
      importMode: form.importMode,
      promptOverride: form.promptOverride.trim() || undefined,
      maxTokens: form.maxTokens
    })
    ElMessage.success('图谱预览生成成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文本抽取失败')
  } finally {
    extractingText.value = false
  }
}

async function submitFile() {
  if (!selectedFile.value) {
    return
  }
  extractingFile.value = true
  importResult.value = null
  try {
    preview.value = await extractGraphFile({
      file: selectedFile.value,
      graphSpace: form.graphSpace.trim() || undefined,
      title: form.title.trim() || undefined,
      llmModel: form.llmModel.trim() || undefined,
      extractMode: form.extractMode,
      importMode: form.importMode,
      promptOverride: form.promptOverride.trim() || undefined,
      maxTokens: form.maxTokens
    })
    ElMessage.success('文件图谱预览生成成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件抽取失败')
  } finally {
    extractingFile.value = false
  }
}

async function confirmImport() {
  if (!preview.value) {
    return
  }
  if (importing.value) {
    return
  }
  if (importResult.value?.previewId === preview.value.previewId) {
    ElMessage.warning('当前预览已完成导入，请先重新抽取或执行回滚后再导入')
    return
  }
  importing.value = true
  try {
    const idempotencyKey = `graph-import-${preview.value.previewId}`
    importResult.value = await importGraphPreview(
      {
        previewId: preview.value.previewId,
        graphSpace: preview.value.graphSpace,
        importMode: preview.value.importMode
      },
      idempotencyKey
    )
    ElMessage.success('图谱导入成功')
    await loadEntities()
    await loadRelationships()
    await loadSpaces()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图谱导入失败')
  } finally {
    importing.value = false
  }
}

async function loadEntities() {
  loadingEntities.value = true
  try {
    entityPage.value = await pageGraphEntities({
      graphSpace: form.graphSpace.trim() || 'default',
      page: 1,
      pageSize: 20,
      keyword: entityQuery.keyword.trim() || undefined
    })
    graphEntityBuffer.value = entityPage.value.items || []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '实体列表加载失败')
  } finally {
    loadingEntities.value = false
  }
}

async function loadRelationships() {
  loadingRelationships.value = true
  try {
    relationshipPage.value = await pageGraphRelationships({
      graphSpace: form.graphSpace.trim() || 'default',
      page: 1,
      pageSize: 20,
      keyword: relationshipQuery.keyword.trim() || undefined
    })
    graphRelationshipBuffer.value = relationshipPage.value.items || []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关系列表加载失败')
  } finally {
    loadingRelationships.value = false
  }
}

async function removeEntities() {
  if (!selectedEntityNames.value.length) {
    return
  }
  try {
    await deleteGraphEntities({
      graphSpace: (preview.value?.graphSpace || form.graphSpace).trim() || 'default',
      normalizedNames: selectedEntityNames.value
    })
    ElMessage.success('实体删除成功')
    selectedEntityNames.value = []
    await loadEntities()
    await loadRelationships()
    await loadSpaces()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '实体删除失败')
  }
}

async function rollbackImportBatch() {
  if (!importResult.value?.importBatchId) {
    return
  }
  rollingBackBatch.value = true
  try {
    await deleteGraphImportBatch({
      graphSpace: importResult.value.graphSpace,
      importBatchId: importResult.value.importBatchId
    })
    ElMessage.success('本次导入已回滚')
    importResult.value = null
    await loadEntities()
    await loadRelationships()
    await loadSpaces()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批次回滚失败')
  } finally {
    rollingBackBatch.value = false
  }
}

async function removeRelationships() {
  if (!selectedRelationshipIds.value.length) {
    return
  }
  try {
    await deleteGraphRelationships({
      graphSpace: (preview.value?.graphSpace || form.graphSpace).trim() || 'default',
      relationshipIds: selectedRelationshipIds.value
    })
    ElMessage.success('关系删除成功')
    selectedRelationshipIds.value = []
    await loadRelationships()
    await loadSpaces()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关系删除失败')
  }
}
</script>

<style scoped>
/* ---- 整体页面 ---- */
.graph-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 0;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-shrink: 0;
  margin-bottom: 12px;
}
.page-header h1 { margin: 0; font-size: 22px; }
.page-header p  { margin: 4px 0 0; color: #606266; font-size: 13px; }

/* ---- 主体双栏 ---- */
.graph-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  overflow: hidden;
}

/* 左栏：固定宽度，不出现滚动条 */
.left-col {
  width: 360px;
  flex-shrink: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.left-col .full-card {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
:deep(.left-col .el-card__body) {
  flex: 1;
  overflow: hidden;
  padding: 14px;
}

/* 右栏：三行等高各自独立滚动 */
.right-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
  padding-bottom: 16px;
  box-sizing: border-box;
}

.right-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.right-panel .full-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
:deep(.right-panel .el-card__body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
}
:deep(.right-panel .el-card__header) {
  padding: 8px 14px;
  flex-shrink: 0;
}

/* ---- 表格填满卡片内容区 ---- */
.inner-table {
  width: 100%;
  height: 100%;
}
:deep(.inner-table .el-table__body-wrapper) {
  overflow-y: auto;
}

/* ---- 通用 ---- */
.full-card { height: 100%; }

.panel-header { font-weight: 600; font-size: 14px; }

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.graph-field { margin-bottom: 10px; }

.graph-action { margin-top: 8px; }

.field-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.action-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.sub-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.graph-hint,
.file-name {
  color: #909399;
  font-size: 12px;
}

.success-text {
  color: #67c23a;
  font-size: 12px;
}

.graph-dialog-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

/* 全屏弹框：消除滚动条，内容区 flex 布局让 canvas 填满剩余高度 */
:deep(.graph-fullscreen-dialog .el-dialog__body) {
  height: calc(100vh - 54px); /* 减去 header 高度 */
  padding: 12px 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── 图谱 canvas + 右侧面板容器 ── */
.graph-canvas-wrap {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  flex: 1;
  min-height: 0;
}

/* ── 右侧属性面板 ── */
.node-detail-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: 320px;
  height: 100%;
  background: #ffffff;
  border-left: 1px solid #e4e8f0;
  display: flex;
  flex-direction: column;
  z-index: 10;
  overflow: hidden;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
}

/* 滑入动效：纯 CSS transition，不依赖 Vue <Transition>，避免 BaseTransition 内 null vnode 崩溃 */
.node-detail-panel {
  transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.22s ease;
  transform: translateX(100%);
  opacity: 0;
  pointer-events: none;
}
.node-detail-panel.panel-visible {
  transform: translateX(0);
  opacity: 1;
  pointer-events: auto;
}

.ndp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 8px;
  border-bottom: 1px solid #e4e8f0;
  flex-shrink: 0;
  background: #f8f9fc;
}

.ndp-label-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ndp-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ndp-kind-tag {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.ndp-close-btn {
  color: #94a3b8 !important;
  padding: 2px !important;
}
.ndp-close-btn:hover {
  color: #334155 !important;
}

.ndp-node-title {
  padding: 10px 14px 12px;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  border-bottom: 1px solid #e4e8f0;
  flex-shrink: 0;
  word-break: break-all;
  line-height: 1.4;
}

.ndp-node-type-line {
  padding: 8px 14px;
  font-size: 12px;
  color: #475569;
  background: #f8f9fc;
  border-bottom: 1px solid #e4e8f0;
  flex-shrink: 0;
}

.ndp-section-title {
  padding: 8px 14px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #3b82f6;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  flex-shrink: 0;
}

.ndp-props-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 0 12px;
}

.ndp-prop-row {
  display: flex;
  flex-direction: column;
  padding: 6px 14px;
  border-bottom: 1px solid #f1f5f9;
  gap: 2px;
}
.ndp-prop-row:hover {
  background: #f8faff;
}

.ndp-prop-key {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.ndp-prop-val {
  font-size: 13px;
  color: #1e293b;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 120px;
  overflow-y: auto;
  line-height: 1.5;
}

.ndp-empty {
  padding: 20px 14px;
  color: #94a3b8;
  font-size: 13px;
}
</style>
