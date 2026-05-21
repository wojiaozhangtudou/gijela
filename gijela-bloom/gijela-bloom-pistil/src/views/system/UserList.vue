<template>
  <div>
    <div v-if="lastChange" style="margin-bottom:12px;display:flex;align-items:center;justify-content:space-between">
      <div>{{ $t('message.undo') }}: <strong>{{ lastChange.username }}</strong></div>
      <div>
        <el-button size="mini" @click="undoLastChange">{{ $t('message.undo') }}</el-button>
      </div>
    </div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:12px">
  <el-input v-model="filters.username" :placeholder="$t('username')" style="width:200px" />
        <div>
            <el-button type="primary" @click="onSearch">{{ $t('message.search') }}</el-button>
            <el-button @click="onReset">{{ $t('message.reset') }}</el-button>
            <el-button v-if="canCreate" type="primary" @click="openDialog()">{{ $t('message.create') }}</el-button>
        </div>
      </div>

  <el-table :data="list" style="width:100%" :loading="tableLoading">
  <el-table-column prop="id" :label="$t('message.id')" width="80" />
  <el-table-column prop="username" :label="$t('username')" />
  <el-table-column prop="nickname" :label="$t('message.fullname')" />
  <el-table-column prop="email" :label="$t('message.email')" />
  <el-table-column prop="phone" :label="$t('message.phone')" />
  <el-table-column prop="status" :label="$t('message.status')" width="140">
          <template #default="{ row }">
            <!-- Use one-way binding for switch and call onStatusChange with new value. Only update UI after API success. -->
                <div class="status-cell"><el-switch class="status-switch" :model-value="row.status" @mousedown="() => (userClicked = true)" @change="handleSwitchChange(row, $event)" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" /></div>
          </template>
        </el-table-column>
  <el-table-column :label="$t('message.actions')" width="280">
          <template #default="{ row }">
            <el-button v-permission="'user:update'" size="mini" @click="openDialog(row)">{{ $t('message.edit') }}</el-button>
            <el-button v-permission="'user:delete'" size="mini" type="danger" @click="onDelete(row)">{{ $t('message.delete') }}</el-button>
            <el-button v-permission="'user:resetPwd'" size="mini" @click="onResetPwd(row)">{{ $t('message.password_reset') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

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

  <el-dialog :title="dialogTitle" v-model="dialogVisible">
      <el-form :model="form" ref="formRef" :rules="rules">
  <el-form-item :label="$t('username')">
          <el-input v-model="form.username" />
        </el-form-item>
  <el-form-item :label="$t('message.fullname')">
          <el-input v-model="form.nickname" />
        </el-form-item>
  <el-form-item :label="$t('message.email')">
          <el-input v-model="form.email" />
        </el-form-item>
  <el-form-item :label="$t('message.phone')">
          <el-input v-model="form.phone" />
        </el-form-item>
  <el-form-item :label="$t('message.status')">
          <el-switch v-model="form.status" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" />
        </el-form-item>
      </el-form>
      <template #footer>
  <el-button @click="dialogVisible = false">{{ $t('message.cancel') }}</el-button>
  <el-button type="primary" :loading="saving" @click="onSave">{{ $t('message.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { pageUsers, saveUser, deleteUsers, changeUserStatus, resetUserPwd } from '../../api/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useI18n } from 'vue-i18n'
import { hasPermission } from '../../utils/permission'

export default defineComponent({
  setup() {
  const { t } = useI18n()
    const filters = reactive({ username: '', status: '' })
    const page = reactive({ current: 1, size: 20 })
    const total = ref(0)
    const list = ref<any[]>([])
    const tableLoading = ref(false)
  const ignoreStatusChange = ref(false)

  // track last status change for quick undo
  const lastChange = ref<{ id: any; username: string; prevStatus: any; newStatus: any; ts: number } | null>(null)
  let undoTimer: number | null = null

  const dialogVisible = ref(false)
    const dialogTitle = ref('')
  const userClicked = ref(false)
    const form = reactive<any>({ id: null, username: '', nickname: '', email: '', phone: '', status: '1' })
    const saving = ref(false)
    const formRef = ref()
    const rules = {
        username: [{ required: true, message: String(t('validation.enter_username')), trigger: 'blur' }],
        nickname: [{ required: true, message: String(t('validation.enter_fullname')), trigger: 'blur' }],
        email: [{ type: 'email', message: String(t('validation.invalid_email')), trigger: 'blur' }],
        phone: [{ pattern: /^\d{6,15}$/, message: String(t('validation.invalid_phone')), trigger: 'blur' }]
      }

    async function load() {
      const payload = { current: page.current, size: page.size, username: filters.username, status: filters.status }
      tableLoading.value = true
      try {
        const r = await pageUsers(payload)
        // ApiResponse: { code, msg, data }
        const d = r?.data || { records: [], total: 0 }
  // Try common structures
  // While assigning data, ignore switch change events to avoid spurious API calls
  ignoreStatusChange.value = true
  // normalize status to string '1' or '0'
  const records = (d.records || d || [])
  list.value = records.map((it: any) => ({ ...it, status: it.status == null ? '0' : String(it.status) }))
  // allow DOM update to settle before enabling status changes
  await nextTick()
  ignoreStatusChange.value = false
  total.value = d.total || (Array.isArray(d) ? d.length : 0)
      } catch (e) {
        // show notification
        ElMessage.error(String(t('message.load_users_failed')))
      } finally {
        tableLoading.value = false
      }
    }

    function onSearch() {
      page.current = 1
      load()
    }

    function onReset() {
      filters.username = ''
      filters.status = ''
      onSearch()
    }

    function onPageChange(p: number) {
      page.current = p
      load()
    }

    function onSizeChange(s: number) {
      page.size = s
      load()
    }

    const router = useRouter()
  function openDialog(row?: any) {
      // prefer full page create/edit, fallback to dialog
      if (row) {
        router.push({ path: `/system/users/${row.id}/edit` })
        return
      }
      router.push({ path: '/system/users/create' })
    }

  // use exact backend permission strings
  const canCreate = computed(() => hasPermission('user:create'))
  const canEdit = computed(() => hasPermission('user:update'))
  const canDelete = computed(() => hasPermission('user:delete'))
  const canResetPwd = computed(() => hasPermission('user:resetPwd'))

    async function onSave() {
      // validate first
      if (!formRef.value) {
        saving.value = true
        try {
          const res: any = await saveUser(form)
          showApiResult(res, String(t('message.save_success')), () => { dialogVisible.value = false; load() })
        } catch (e) {
          ElMessage.error(String(t('message.save_failed')))
        } finally { saving.value = false }
        return
      }
      (formRef.value as any).validate(async (valid: boolean) => {
        if (!valid) return
        saving.value = true
        try {
          const res: any = await saveUser(form)
          showApiResult(res, String(t('message.save_success')), () => { dialogVisible.value = false; load() })
        } catch (e) {
          ElMessage.error(String(t('message.save_failed')))
        } finally { saving.value = false }
      })
    }

    async function onDelete(row: any) {
      try {
          await ElMessageBox.confirm(`${row.username} ` + String(t('message.delete_confirm')), String(t('message.confirm')), { type: 'warning' })
    const res: any = await deleteUsers({ ids: [row.id] })
    showApiResult(res, String(t('message.delete_success')), () => load())
      } catch (e) {
        // cancelled or failed
      }
    }

    async function onStatusChange(row: any, newStatus: any) {
      // normalize to string '1' or '0'
      newStatus = String(newStatus)
      const action = newStatus === '1' ? String(t('message.activate')) : String(t('message.disable'))
      try {
        await ElMessageBox.confirm(`${action} ` + row.username + ' ? ' , String(t('message.confirm')), { type: 'warning' })
        // call API with requested status
        const res: any = await changeUserStatus({ id: row.id, status: newStatus })
        showApiResult(res, String(t('message.status_update_success')), () => {
          const prev = String(row.status)
          row.status = String(newStatus)
          lastChange.value = { id: row.id, username: row.username, prevStatus: prev, newStatus: newStatus, ts: Date.now() }
          if (undoTimer) { clearTimeout(undoTimer) }
          undoTimer = window.setTimeout(() => { lastChange.value = null; undoTimer = null }, 60000)
          load()
        })
      } catch (e) {
        // cancelled or API failed: do not modify UI (switch keeps previous visual state)
        if (e && e === 'cancel') {
          // user cancelled, nothing to show
        } else {
          ElMessage.error(String(t('message.status_update_failed')))
        }
      }
    }

    function handleSwitchChange(row: any, val: any) {
      // only treat as user action when userClicked is true and not during ignore phase
      if (ignoreStatusChange.value) return
      if (!userClicked.value) return
      // reset flag and handle
      userClicked.value = false
      onStatusChange(row, String(val))
    }

    async function undoLastChange() {
      if (!lastChange.value) return
      const lc = lastChange.value
      try {
        const res: any = await changeUserStatus({ id: lc.id, status: lc.prevStatus })
        showApiResult(res, String(t('message.undo_success')), () => {
          lastChange.value = null
          if (undoTimer) { clearTimeout(undoTimer); undoTimer = null }
          load()
        })
      } catch (e) {
  ElMessage.error(String(t('message.undo_failed')))
      }
    }

    async function onResetPwd(row: any) {
      try {
  await ElMessageBox.confirm(`${row.username} ` + String(t('message.password_reset_confirm')), String(t('message.confirm')), { type: 'warning' })
  const res: any = await resetUserPwd({ id: row.id, password: '123456' })
  showApiResult(res, String(t('message.password_reset_success')))
      } catch (e) {
        // cancelled or failed
      }
    }

  onMounted(() => { load() })

  return { filters, page, total, list, tableLoading, dialogVisible, dialogTitle, form, formRef, rules, saving, onSearch, onReset, onPageChange, onSizeChange, openDialog, onSave, onDelete, onStatusChange, onResetPwd, lastChange, undoLastChange, handleSwitchChange, userClicked, canCreate, canEdit, canDelete, canResetPwd }
  }
})
</script>
