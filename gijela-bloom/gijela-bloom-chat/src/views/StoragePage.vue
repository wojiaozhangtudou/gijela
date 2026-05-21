<template>
  <div class="storage-page">
    <div class="page-header">
      <div>
        <h1>对象存储管理</h1>
        <p>支持列桶、建桶、删桶、文件列表、上传与删除。</p>
      </div>
      <el-space>
        <el-button plain @click="goChatPage">返回联调工作台</el-button>
      </el-space>
    </div>

    <el-row :gutter="16" class="storage-layout">
      <el-col :span="8" class="panel-col">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="panel-header-row">
              <span>桶管理</span>
              <el-button link :loading="loadingBuckets" @click="loadBuckets">刷新</el-button>
            </div>
          </template>

          <div class="bucket-create-row">
            <el-input v-model="newBucket" placeholder="新桶名，例如 resume-rag" />
            <el-button type="primary" plain :disabled="!newBucket.trim()" :loading="creatingBucket" @click="handleCreateBucket">建桶</el-button>
          </div>

          <el-empty v-if="!buckets.length" description="暂无桶" :image-size="72" />
          <el-scrollbar v-else class="bucket-scroll">
            <div
              v-for="item in buckets"
              :key="item.name"
              class="bucket-item"
              :class="{ active: selectedBucket === item.name }"
              @click="selectBucket(item.name)"
            >
              <span class="bucket-name">{{ item.name }}</span>
              <el-button link type="danger" @click.stop="handleDeleteBucket(item.name)">删除</el-button>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>

      <el-col :span="16" class="panel-col">
        <el-card shadow="never" class="result-card">
          <template #header>
            <div class="panel-header-row">
              <span>文件列表</span>
              <el-space>
                <el-input v-model="prefix" placeholder="prefix 过滤（可选）" style="width: 220px" @keyup.enter="loadObjects" />
                <el-button type="primary" :disabled="!selectedBucket" :loading="loadingObjects" @click="loadObjects">查询</el-button>
              </el-space>
            </div>
          </template>

          <div class="upload-row">
            <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange">
              <el-button plain :disabled="!selectedBucket">选择文件</el-button>
            </el-upload>
            <el-input v-model="uploadObjectKey" placeholder="对象 key（可选，默认文件名）" style="width: 280px" />
            <el-button
              type="success"
              plain
              :disabled="!selectedBucket || !uploadFile"
              :loading="uploading"
              @click="handleUpload"
            >上传</el-button>
          </div>

          <div v-if="selectedFileName" class="selected-file">已选文件：{{ selectedFileName }}</div>

          <el-empty v-if="!objects.length" description="暂无文件" :image-size="80" />
          <el-table v-else :data="objects" stripe size="small" style="width: 100%">
            <el-table-column prop="key" label="对象 Key" min-width="280" />
            <el-table-column prop="size" label="大小" width="120">
              <template #default="scope">{{ formatSize(scope.row.size) }}</template>
            </el-table-column>
            <el-table-column prop="lastModified" label="更新时间" min-width="180" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="handleDownloadObject(scope.row.key)">下载</el-button>
                <el-button link type="danger" @click="handleDeleteObject(scope.row.key)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createStorageBucket,
  deleteStorageBucket,
  deleteStorageObject,
  downloadStorageObject,
  listStorageBuckets,
  listStorageObjects,
  uploadStorageObject
} from '@/api/chat'
import type { StorageBucketItem, StorageObjectItem } from '@/types/chat'

const router = useRouter()

const loadingBuckets = ref(false)
const loadingObjects = ref(false)
const creatingBucket = ref(false)
const uploading = ref(false)

const buckets = ref<StorageBucketItem[]>([])
const objects = ref<StorageObjectItem[]>([])
const selectedBucket = ref('')
const newBucket = ref('')
const prefix = ref('')

const uploadFile = ref<File | null>(null)
const selectedFileName = ref('')
const uploadObjectKey = ref('')

onMounted(() => {
  void loadBuckets()
})

function goChatPage() {
  void router.push('/')
}

async function loadBuckets() {
  loadingBuckets.value = true
  try {
    const data = await listStorageBuckets()
    buckets.value = data.buckets || []
    if (selectedBucket.value && !buckets.value.some((item) => item.name === selectedBucket.value)) {
      selectedBucket.value = ''
      objects.value = []
    }
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '加载桶列表失败'))
  } finally {
    loadingBuckets.value = false
  }
}

function selectBucket(name: string) {
  selectedBucket.value = name
  void loadObjects()
}

async function handleCreateBucket() {
  const bucket = newBucket.value.trim()
  if (!bucket) {
    return
  }
  creatingBucket.value = true
  try {
    const result = await createStorageBucket(bucket)
    ElMessage.success(`建桶完成：${String(result.status || 'ok')}`)
    newBucket.value = ''
    await loadBuckets()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '建桶失败'))
  } finally {
    creatingBucket.value = false
  }
}

async function handleDeleteBucket(bucket: string) {
  try {
    await ElMessageBox.confirm(`确认删除桶 ${bucket}？（桶必须为空）`, '删除桶', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteStorageBucket(bucket)
    ElMessage.success('桶已删除')
    if (selectedBucket.value === bucket) {
      selectedBucket.value = ''
      objects.value = []
    }
    await loadBuckets()
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(resolveApiErrorMessage(error, '删除桶失败'))
  }
}

async function loadObjects() {
  if (!selectedBucket.value) {
    objects.value = []
    return
  }
  loadingObjects.value = true
  try {
    const data = await listStorageObjects({
      bucket: selectedBucket.value,
      prefix: prefix.value.trim() || undefined,
      maxKeys: 200
    })
    objects.value = data.objects || []
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '加载文件列表失败'))
  } finally {
    loadingObjects.value = false
  }
}

function onFileChange(file: { raw?: File }) {
  uploadFile.value = file.raw || null
  selectedFileName.value = file.raw?.name || ''
  if (!uploadObjectKey.value && file.raw?.name) {
    uploadObjectKey.value = file.raw.name
  }
}

async function handleUpload() {
  if (!selectedBucket.value || !uploadFile.value) {
    return
  }
  const objectKey = (uploadObjectKey.value || uploadFile.value.name || '').trim()
  if (!objectKey) {
    ElMessage.warning('对象 key 不能为空')
    return
  }
  uploading.value = true
  try {
    await uploadStorageObject({
      bucket: selectedBucket.value,
      objectKey,
      file: uploadFile.value
    })
    ElMessage.success('上传成功')
    uploadFile.value = null
    selectedFileName.value = ''
    uploadObjectKey.value = ''
    await loadObjects()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function handleDeleteObject(key: string) {
  if (!selectedBucket.value) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除对象 ${key}？`, '删除对象', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteStorageObject(selectedBucket.value, key)
    ElMessage.success('对象已删除')
    await loadObjects()
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(resolveApiErrorMessage(error, '删除对象失败'))
  }
}

async function handleDownloadObject(key: string) {
  if (!selectedBucket.value) {
    return
  }
  try {
    const response = await downloadStorageObject(selectedBucket.value, key)
    const contentTypeHeader = response.headers?.['content-type']
    const contentType = typeof contentTypeHeader === 'string' ? contentTypeHeader : 'application/octet-stream'
    const blob = new Blob([response.data], { type: contentType })
    const objectUrl = window.URL.createObjectURL(blob)
    const fileName = key.includes('/') ? key.substring(key.lastIndexOf('/') + 1) : key
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = fileName || 'object.bin'
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(objectUrl)
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '下载对象失败'))
  }
}

function formatSize(size?: number | null) {
  if (typeof size !== 'number' || Number.isNaN(size)) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(2)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function resolveApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { msg?: string } }; message?: string }
  const backendMsg = maybe?.response?.data?.msg
  if (typeof backendMsg === 'string' && backendMsg.trim()) {
    return backendMsg
  }
  if (typeof maybe?.message === 'string' && maybe.message.trim()) {
    return maybe.message
  }
  return fallback
}
</script>

<style scoped>
.storage-page {
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

.storage-layout {
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
  min-height: 0;
}

.side-card {
  flex: 1;
}

.result-card {
  flex: 1;
}

.bucket-create-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.bucket-scroll {
  max-height: calc(100vh - 260px);
}

.bucket-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f9fc;
  margin-bottom: 8px;
  cursor: pointer;
}

.bucket-item.active {
  background: #e1f0ff;
  border: 1px solid #b3d8ff;
}

.bucket-name {
  font-size: 13px;
  font-weight: 500;
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.selected-file {
  margin-bottom: 10px;
  font-size: 12px;
  color: #606266;
}
</style>
