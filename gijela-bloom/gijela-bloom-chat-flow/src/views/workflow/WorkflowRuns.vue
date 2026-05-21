<template>
  <el-card>
    <template #header><strong>运行历史</strong></template>

    <el-space style="margin-bottom: 12px">
      <el-select v-model="runType" placeholder="运行类型" clearable style="width: 140px">
        <el-option label="调试" value="debug" />
        <el-option label="正式" value="prod" />
      </el-select>
      <el-select v-model="status" placeholder="状态" clearable style="width: 140px">
        <el-option label="成功" value="success" />
        <el-option label="失败" value="failed" />
        <el-option label="运行中" value="running" />
      </el-select>
      <el-button type="primary" @click="pageNum = 1; load()">查询</el-button>
    </el-space>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="runId" label="运行ID" min-width="210" />
      <el-table-column prop="runType" label="类型" width="100" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="workflowVersion" label="版本" width="90" />
      <el-table-column prop="durationMs" label="耗时(ms)" width="110" />
      <el-table-column label="操作" width="110">
        <template #default="scope">
          <el-button size="small" @click="openDetail(scope.row.runId)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="pageNum = 1; load()"
      />
    </div>

    <el-drawer v-model="drawer" title="运行详情" size="60%">
      <pre style="white-space: pre-wrap">{{ detailText }}</pre>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRunDetail, listRuns } from '../../api/workflow'
import type { WorkflowRunSummary } from '../../types/workflow'

const route = useRoute()
const workflowId = String(route.params.id)

const loading = ref(false)
const runType = ref<string | undefined>()
const status = ref<string | undefined>()
const rows = ref<WorkflowRunSummary[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const drawer = ref(false)
const detailText = ref('')

async function load() {
  loading.value = true
  try {
    const page = await listRuns(workflowId, {
      runType: runType.value,
      status: status.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    rows.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

async function openDetail(runId: string) {
  const detail = await getRunDetail(runId)
  detailText.value = JSON.stringify(detail, null, 2)
  drawer.value = true
}

onMounted(load)
</script>
