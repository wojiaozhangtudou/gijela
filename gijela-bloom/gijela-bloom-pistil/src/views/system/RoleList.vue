<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:12px">
  <el-input v-model="filters.name" :placeholder="$t('message.role_name')" style="width:200px" />
        <div>
          <el-button type="primary" @click="onSearch">{{ $t('message.search') }}</el-button>
          <el-button @click="onReset">{{ $t('message.reset') }}</el-button>
          <el-button v-if="canCreate" type="primary" @click="openDialog()">{{ $t('message.create') }}</el-button>
        </div>
      </div>

      <el-table :data="list" style="width:100%" :loading="tableLoading">
  <el-table-column prop="id" :label="$t('message.id')" width="80" />
  <el-table-column prop="name" :label="$t('message.role_name')" />
  <el-table-column prop="code" :label="$t('message.code')" />
  <el-table-column prop="status" :label="$t('message.status')" width="140">
          <template #default="{ row }">
            <div class="status-cell"><el-switch class="status-switch" :model-value="row.status" @mousedown="() => (userClicked = true)" @change="onSwitch(row, $event)" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" /></div>
          </template>
        </el-table-column>
  <el-table-column :label="$t('message.actions')" width="280">
          <template #default="{ row }">
            <el-button v-if="canEdit" size="mini" @click="openDialog(row)">{{ $t('message.edit') }}</el-button>
            <el-button v-if="canDelete" size="mini" type="danger" @click="onDelete(row)">{{ $t('message.delete') }}</el-button>
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
  <el-form-item :label="$t('message.role_name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
  <el-form-item :label="$t('message.code')" prop="code">
          <el-input v-model="form.code" />
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
import { defineComponent, reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { pageRoles, saveRole, deleteRoles, changeRoleStatus } from '../../api/role'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useI18n } from 'vue-i18n'
import { hasPermission } from '../../utils/permission'

export default defineComponent({
  setup() {
  const { t } = useI18n()
    const router = useRouter()
    const filters = reactive({ name: '' })
    const page = reactive({ current: 1, size: 20 })
    const total = ref(0)
    const list = ref<any[]>([])
    const tableLoading = ref(false)

  const ignoreStatusChange = ref(false)
  const userClicked = ref(false)

    const dialogVisible = ref(false)
    const dialogTitle = ref('')
    const form = reactive<any>({ id: null, name: '', code: '' })
    const saving = ref(false)
    const formRef = ref()
  const rules = { name: [{ required: true, message: String(t('validation.enter_role_name')), trigger: 'blur' }] }

    async function load() {
      tableLoading.value = true
      try {
        const r = await pageRoles({ current: page.current, size: page.size, name: filters.name })
        const d = r?.data || { records: [], total: 0 }
        // normalize status to string '1' or '0'
        const records = (d.records || d || [])
        list.value = (records || []).map((it: any) => ({ ...it, status: it.status == null ? '0' : String(it.status) }))
        total.value = d.total || (Array.isArray(d) ? d.length : 0)
      } catch (e) {
        ElMessage.error(String(t('message.load_roles_failed')))
      } finally { tableLoading.value = false }
    }

    function onSearch() { page.current = 1; load() }
    function onReset() { filters.name = ''; onSearch() }
    function onPageChange(p: number) { page.current = p; load() }
    function onSizeChange(s: number) { page.size = s; load() }

    function openDialog(row?: any) {
      console.info('[debug] RoleList.openDialog row=', row)
      if (row) {
        // navigate to edit page
        router.push({ path: `/sys/roles/${row.id}/edit` })
        return
      }
      // navigate to create page
      // ensure default status is a string '1'
      router.push({ path: '/sys/roles/create' })
    }

  // exact backend permissions
  const canCreate = computed(() => hasPermission('role:create'))
  const canEdit = computed(() => hasPermission('role:update'))
  const canDelete = computed(() => hasPermission('role:delete'))
  const canAssign = computed(() => hasPermission('role:assign'))

    async function onSave() {
  console.info('[debug] RoleList.onSave payload=', JSON.parse(JSON.stringify(form)))
  if (!formRef.value) {
    saving.value = true
    try {
      const res: any = await saveRole(form)
      showApiResult(res, String(t('message.save_success')), () => { dialogVisible.value = false; load() })
    } catch (e: any) {
      const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.save_failed'))
      ElMessage.error(String(m))
    } finally { saving.value = false }
    return
  }
      (formRef.value as any).validate(async (valid: boolean) => {
        if (!valid) return
        saving.value = true
        try {
          const res: any = await saveRole(form)
          showApiResult(res, String(t('message.save_success')), () => { dialogVisible.value = false; load() })
        } catch (e: any) {
          const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.save_failed'))
          ElMessage.error(String(m))
        } finally { saving.value = false }
      })
    }

    async function onDelete(row: any) {
      console.info('[debug] RoleList.onDelete row=', row)
      try {
          await ElMessageBox.confirm(`${row.name} ` + String(t('message.delete_confirm')), String(t('message.confirm')), { type: 'warning' })
    const res: any = await deleteRoles({ ids: [row.id] })
    showApiResult(res, String(t('message.delete_success')), () => load())
      } catch (e: any) {
        if (e && e === 'cancel') return
        const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.delete_failed'))
        ElMessage.error(String(m))
      }
    }

    async function onStatusChange(row: any, newStatus: any) {
      newStatus = String(newStatus)
      const action = newStatus === '1' ? String(t('message.activate')) : String(t('message.disable'))
      try {
        await ElMessageBox.confirm(`${action} ` + row.name + ' ? ' , String(t('message.confirm')), { type: 'warning' })
        const res: any = await changeRoleStatus({ id: row.id, status: newStatus })
        showApiResult(res, String(t('message.status_update_success')), () => {
          row.status = String(newStatus)
          load()
        })
      } catch (e: any) {
        if (e && e === 'cancel') return
        const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.status_update_failed'))
        ElMessage.error(String(m))
      }
    }

    function handleSwitchChange(row: any, val: any) {
      if (ignoreStatusChange.value) return
      if (!userClicked.value) return
      userClicked.value = false
      onStatusChange(row, String(val))
    }

    function onSwitch(row: any, val: any) {
      handleSwitchChange(row, val)
    }

  onMounted(() => { load() })
  return { filters, page, total, list, tableLoading, dialogVisible, dialogTitle, form, formRef, rules, saving, onSearch, onReset, onPageChange, onSizeChange, openDialog, onSave, onDelete, onStatusChange, handleSwitchChange, onSwitch, userClicked, ignoreStatusChange, canCreate, canEdit, canDelete, canAssign }
  }
})
</script>

<!-- keep original project styles; no local action-btn overrides required -->
