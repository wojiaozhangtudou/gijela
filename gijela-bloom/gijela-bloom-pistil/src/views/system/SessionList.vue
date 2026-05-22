<template>
  <div class="session-list-page">
    <el-card>
      <div class="session-toolbar">
        <div class="session-toolbar-left">
          <el-input
            v-model="filters.userId"
            :placeholder="$t('message.session_user_id_placeholder')"
            class="toolbar-input"
            :disabled="isSelfMode"
            clearable
          />
          <el-input
            v-if="!isSelfMode"
            v-model="filters.username"
            :placeholder="$t('message.session_username_placeholder')"
            class="toolbar-input"
            clearable
          />
        </div>
        <div class="session-toolbar-right">
          <el-button type="primary" @click="onSearch">{{ $t('message.search') }}</el-button>
          <el-button @click="onReset">{{ $t('message.reset') }}</el-button>
          <el-button v-if="canKickoutAll" type="danger" :disabled="!filters.userId" @click="onKickoutAll">{{ $t('message.kickout_all') }}</el-button>
        </div>
      </div>

      <div class="session-table-wrap">
        <el-table :data="list" style="width:100%" :loading="tableLoading" :max-height="520" table-layout="fixed">
          <el-table-column prop="sessionId" :label="$t('message.session_id')" width="180" show-overflow-tooltip />
          <el-table-column prop="userId" :label="$t('message.id')" width="80" />
          <el-table-column prop="username" :label="$t('username')" width="120" show-overflow-tooltip />
          <el-table-column prop="deviceNo" :label="$t('message.device_no')" width="150" show-overflow-tooltip />
          <el-table-column prop="loginIp" :label="$t('message.login_ip')" width="120" show-overflow-tooltip />
          <el-table-column prop="clientLabel" :label="$t('message.client_label')" width="160" show-overflow-tooltip />
          <el-table-column prop="loginAt" :label="$t('message.login_at')" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTime(row.loginAt) }}</template>
          </el-table-column>
          <el-table-column prop="lastActiveAt" :label="$t('message.last_active_at')" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTime(row.lastActiveAt) }}</template>
          </el-table-column>
          <el-table-column :label="$t('message.actions')" width="100">
            <template #default="{ row }">
              <el-button v-if="canKickout" size="small" type="danger" @click="onKickout(row)">{{ $t('message.kickout') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div style="margin-top:12px;text-align:right">
        <el-pagination
          background
          layout="prev, pager, next, sizes, total"
          :total="total"
          :page-size="page.size"
          :current-page="page.current"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { showApiResult } from '../../utils/apiResult'
import { pageLoginSessions, kickoutLoginSession, kickoutAllLoginSessions } from '../../api/auth'
import { hasPermission } from '../../utils/permission'

export default defineComponent({
  setup() {
    const route = useRoute()
    const { t } = useI18n()
    const filters = reactive({ userId: '', username: '' })
    const page = reactive({ current: 1, size: 20 })
    const total = ref(0)
    const list = ref<any[]>([])
    const tableLoading = ref(false)
    const isSelfMode = ref(false)
    const selfUserId = ref<number | null>(null)
    const canKickout = computed(() => isSelfMode.value || hasPermission('auth:session:kickout'))
    const canKickoutAll = computed(() => isSelfMode.value || hasPermission('auth:session:kickoutAll'))

    function parseUserIdStrict(): number | null {
      if (!filters.userId) {
        ElMessage.warning(String(t('message.session_user_id_required')))
        return null
      }
      const id = Number(filters.userId)
      if (!Number.isInteger(id) || id <= 0) {
        ElMessage.warning(String(t('message.session_user_id_invalid')))
        return null
      }
      return id
    }

    function formatTime(ts?: number) {
      if (!ts) return '-'
      const d = new Date(ts)
      if (Number.isNaN(d.getTime())) return '-'
      return d.toLocaleString()
    }

    async function load() {
      let userId: number | null = null
      if (isSelfMode.value && selfUserId.value) {
        userId = selfUserId.value
      } else if (filters.userId && filters.userId.trim()) {
        const id = Number(filters.userId)
        if (!Number.isInteger(id) || id <= 0) {
          ElMessage.warning(String(t('message.session_user_id_invalid')))
          list.value = []
          total.value = 0
          return
        }
        userId = id
      }

      tableLoading.value = true
      try {
        const payload: any = {
          current: page.current,
          size: page.size,
          username: (filters.username || '').trim() || undefined
        }
        if (userId !== null) payload.userId = userId
        const r = await pageLoginSessions(payload)
        const d = r?.data || { records: [], total: 0 }
        list.value = Array.isArray(d.records) ? d.records : []
        total.value = Number(d.total || 0)
      } catch (e) {
        ElMessage.error(String(t('message.load_sessions_failed')))
      } finally {
        tableLoading.value = false
      }
    }

    function onSearch() {
      page.current = 1
      load()
    }

    function onReset() {
      if (isSelfMode.value && selfUserId.value) {
        filters.userId = String(selfUserId.value)
        filters.username = ''
      } else {
        filters.userId = ''
        filters.username = ''
      }
      page.current = 1
      list.value = []
      total.value = 0
      load()
    }

    function onPageChange(p: number) {
      page.current = p
      load()
    }

    function onSizeChange(s: number) {
      page.size = s
      page.current = 1
      load()
    }

    async function onKickout(row: any) {
      if (!row?.sessionId) return
      try {
        await ElMessageBox.confirm(String(t('message.kickout_confirm')), String(t('message.confirm')), { type: 'warning' })
        const res = await kickoutLoginSession(String(row.sessionId))
        showApiResult(res, String(t('message.kickout_success')), () => load())
      } catch (e) {
        // cancel or failed
      }
    }

    async function onKickoutAll() {
      const userId = isSelfMode.value
        ? selfUserId.value
        : parseUserIdStrict()
      if (!userId) return
      try {
        await ElMessageBox.confirm(String(t('message.kickout_all_confirm')), String(t('message.confirm')), { type: 'warning' })
        const res = await kickoutAllLoginSessions(userId)
        showApiResult(res, String(t('message.kickout_all_success')), () => load())
      } catch (e) {
        // cancel or failed
      }
    }

    onMounted(() => {
      const querySelf = route.query.self
      const queryUserId = route.query.userId
      if (String(querySelf || '') === '1') {
        isSelfMode.value = true
      }
      if (typeof queryUserId === 'string' && queryUserId.trim()) {
        filters.userId = queryUserId.trim()
        const id = Number(queryUserId)
        if (Number.isInteger(id) && id > 0) {
          selfUserId.value = id
        }
      }
      if (isSelfMode.value && selfUserId.value) {
        filters.userId = String(selfUserId.value)
      }
      load()
    })

    return { filters, page, total, list, tableLoading, isSelfMode, canKickout, canKickoutAll, formatTime, onSearch, onReset, onPageChange, onSizeChange, onKickout, onKickoutAll }
  }
})
</script>

<style scoped>
.session-list-page {
  width: 100%;
  max-width: 100%;
  overflow-x: clip;
}

.session-list-page :deep(.el-card__body) {
  min-width: 0;
  overflow-x: hidden;
}

.session-list-page :deep(.el-card) {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.session-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.session-toolbar-left,
.session-toolbar-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.toolbar-input {
  width: 220px;
  max-width: 100%;
}

.session-table-wrap {
  min-width: 0;
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.session-table-wrap :deep(.el-table),
.session-table-wrap :deep(.el-table__inner-wrapper),
.session-table-wrap :deep(.el-table__header-wrapper),
.session-table-wrap :deep(.el-table__body-wrapper) {
  max-width: 100%;
}
</style>
