<template>
  <div>
    <el-card class="dept-card">
      <div class="search-row" style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
        <el-input v-model="filters.name" :placeholder="$t('message.dept_name')" style="width:280px" />
        <div style="margin-left:auto">
          <el-button size="mini" @click="expandAll">{{ $t('message.expand_all') || '展开全部' }}</el-button>
          <el-button size="mini" @click="collapseAll">{{ $t('message.collapse_all') || '折叠全部' }}</el-button>
          <el-button type="primary" @click="onSearch">{{ $t('message.search') }}</el-button>
          <el-button @click="onReset">{{ $t('message.reset') }}</el-button>
          <el-button v-if="canCreate" type="primary" @click="openDialog()">{{ $t('message.create') }}</el-button>
        </div>
      </div>

      <el-table ref="deptTable" :data="list" stripe style="width:100%" v-loading="tableLoading" row-key="id" :tree-props="{ children: 'children' }" :default-expand-all="isExpandAll" :expand-row-keys="expandedKeys">
        <el-table-column prop="name" :label="$t('message.name')">
          <template #default="{ row }">
            <div class="name-cell"><span class="dept-name">{{ row.name }}</span></div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="leader" :label="$t('message.leader')">
          <template #default="{ row }">
            <div style="color:#999;font-size:12px">{{ row.leader || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" :label="$t('message.phone')">
          <template #default="{ row }">
            <div style="color:#999;font-size:12px">{{ row.phone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('message.status')" width="140">
          <template #default="{ row }">
            <div class="status-cell"><el-switch class="status-switch" :model-value="String(row.status)" @mousedown="() => (userClicked = true)" @change="onSwitch(row, $event)" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" /></div>
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="$t('message.sort')" width="100" />
        <el-table-column prop="createTime" :label="$t('message.created_at')" width="180" />
        <el-table-column :label="$t('message.actions')" width="200">
          <template #default="{ row }">
            <el-button v-if="canEdit" size="mini" @click="openDialog(row)">{{ $t('message.edit') }}</el-button>
            <el-button v-if="canDelete" size="mini" type="danger" @click="onDelete(row)">{{ $t('message.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Tree view doesn't use pagination; the full tree is shown -->
    </el-card>

    <el-dialog :title="dialogTitle" v-model="dialogVisible">
      <el-form :model="form" ref="formRef" :rules="rules">
        <el-form-item :label="$t('message.name')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('message.enter_dept_name')" />
        </el-form-item>
        <el-form-item :label="$t('message.parent')" prop="parentId">
          <el-select v-model="form.parentId" clearable :placeholder="$t('message.select_parent_dept')" style="width:100%">
            <el-option v-for="opt in parentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <div style="font-size:12px;color:#999;margin-top:4px">
            共 {{ parentOptions.length }} 个上级部门选项
          </div>
        </el-form-item>
        <el-form-item :label="$t('message.leader')" prop="leader">
          <el-input v-model="form.leader" :placeholder="$t('message.enter_leader')" />
        </el-form-item>
        <el-form-item :label="$t('message.phone')" prop="phone">
          <el-input v-model="form.phone" :placeholder="$t('message.enter_phone')" />
        </el-form-item>
        <el-form-item :label="$t('message.email')" prop="email">
          <el-input v-model="form.email" :placeholder="$t('message.enter_email')" />
        </el-form-item>
        <el-form-item :label="$t('message.sort')" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :placeholder="$t('message.enter_sort')" />
        </el-form-item>
        <el-form-item :label="$t('message.status')" prop="status">
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
import { fetchDeptTree, fetchDeptSelectTree, saveDept, deleteDepts, changeDeptStatus } from '../../api/dept'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useI18n } from 'vue-i18n'
import { hasPermission } from '../../utils/permission'

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const filters = reactive({ name: '' })

    // list state for tree view
    const list = ref<any[]>([])
    const rawTree = ref<any[]>([])
    const tableLoading = ref(false)
    const expandedKeys = ref<any[]>([])
    const deptTable = ref<any>(null)
    const isExpandAll = ref(false)
    const parentOptions = ref<any[]>([])
    const deptOptions = ref<any[]>([])

  const dialogVisible = ref(false)
    const dialogTitle = ref('')
    const form = reactive<any>({ id: null, name: '', parentId: null, leader: '', phone: '', email: '', sort: 0, status: '1' })
    const saving = ref(false)
    const formRef = ref()
    const rules = {
      name: [{ required: true, message: String(t('validation.enter_dept_name')), trigger: 'blur' }],
      parentId: [{ required: false, message: String(t('message.select_parent_dept')), trigger: 'change' }],
      email: [{ type: 'email', message: String(t('validation.invalid_email')), trigger: 'blur' }],
      phone: [{ pattern: /^1[3-9]\d{9}$/, message: String(t('validation.invalid_phone')), trigger: 'blur' }],
      sort: [{ type: 'number', message: '排序必须为数字', trigger: 'blur' }]
    }

  // permissions
  const canCreate = computed(() => hasPermission('dept:create'))
  const canEdit = computed(() => hasPermission('dept:update'))
  const canDelete = computed(() => hasPermission('dept:delete'))

    const ignoreStatusChange = ref(false)
    const userClicked = ref(false)

    async function load() {
      tableLoading.value = true
      try {
        const r = await fetchDeptTree()
        const treeData = r?.data || []
        // 规范化节点，确保 status 为字符串 '0'/'1'，避免后续成为 'null' 字符串导致数值转换异常
        const normalized = Array.isArray(treeData) ? treeData.map(normalizeNode) : []
        rawTree.value = normalized

        // Filter tree based on search
        if (filters.name) {
          list.value = filterTree(normalized, filters.name)
        } else {
          list.value = normalized
        }

        // Load select tree for parent options
        try {
          const selectR = await fetchDeptSelectTree()
          const selectData = selectR?.data || []
          // normalize select-tree as well (some backends return status null)
          const normalizedSelect = Array.isArray(selectData) ? selectData.map(normalizeNode) : []
          deptOptions.value = flattenTree(normalizedSelect).filter((dept: any) => dept.status === '1' || dept.status === '0')
          parentOptions.value = buildSelectOptions(rawTree.value)
          console.log('上级部门选项加载成功:', deptOptions.value.length, '个选项')
        } catch (selectError) {
          console.warn('上级部门选项加载失败，使用树形数据作为备选:', selectError)
          // 如果select-tree API失败，使用tree数据作为备选
          const normalizedTree = Array.isArray(treeData) ? treeData.map(normalizeNode) : []
          deptOptions.value = flattenTree(normalizedTree).filter((dept: any) => dept.status === '1' || dept.status === '0')
          parentOptions.value = buildSelectOptions(normalizedTree)

          // 如果仍然没有数据，提供一些默认选项
          if (deptOptions.value.length === 0) {
            console.warn('没有可用的上级部门数据，使用默认选项')
            deptOptions.value = [
              { id: null, name: '根部门', status: '1' }
            ]
            parentOptions.value = [
              { value: null, label: '根部门' }
            ]
          }
        }
      } catch (e) {
        console.error('部门数据加载失败:', e)
        ElMessage.error(String(t('message.load_depts_failed')))
        // 即使主API失败，也要确保deptOptions有值
        deptOptions.value = [
          { id: null, name: '根部门', status: '1' }
        ]
        list.value = []
      } finally {
        tableLoading.value = false
      }
    }

    function filterTree(tree: any[], keyword: string): any[] {
      return tree.filter(node => {
        const matches = node.name.includes(keyword)
        if (node.children && node.children.length > 0) {
          node.children = filterTree(node.children, keyword)
          return matches || node.children.length > 0
        }
        return matches
      })
    }

    function flattenTree(tree: any[]): any[] {
      const result: any[] = []
      function traverse(nodes: any[]) {
        nodes.forEach(node => {
          result.push(node)
          if (node.children && node.children.length > 0) {
            traverse(node.children)
          }
        })
      }
      traverse(tree)
      return result
    }

    // normalize a node: ensure status is string and children exist (fixes null -> 'null' issues)
    function normalizeNode(n: any) {
      const children = n.children && Array.isArray(n.children) ? n.children.map(normalizeNode) : []
      return { ...n, status: n.status == null ? '0' : String(n.status), children }
    }

    function buildSelectOptions(nodes: any[], prefix = '', excludeId?: string): any[] {
      const out: any[] = []
      for (const n of nodes) {
        // 排除指定的节点及其子节点（用于编辑时避免循环引用）
        if (excludeId && n.id === excludeId) continue

        out.push({ value: n.id, label: prefix + (n.name || '') })
        if (n.children && n.children.length) out.push(...buildSelectOptions(n.children, prefix + '└─ ', excludeId))
      }
      return out
    }

    function expandAll() {
      isExpandAll.value = true
      expandedKeys.value = getAllKeys(list.value)
      nextTick(() => {
        deptTable.value?.setScrollTop(0)
      })
    }

    function collapseAll() {
      isExpandAll.value = false
      expandedKeys.value = []
    }

    function getAllKeys(tree: any[]): any[] {
      const keys: any[] = []
      function traverse(nodes: any[]) {
        nodes.forEach(node => {
          keys.push(node.id)
          if (node.children && node.children.length > 0) {
            traverse(node.children)
          }
        })
      }
      traverse(tree)
      return keys
    }

    function onSearch() {
      load()
    }

    function onReset() {
      filters.name = ''
      onSearch()
    }

    function openDialog(row?: any) {
      console.log('打开对话框:', { isEdit: !!row, rowData: row })
      if (row) {
        dialogTitle.value = t('message.edit_dept')
        console.log('编辑模式 - 原始数据:', row)
        // 深拷贝数据，避免引用问题
        Object.assign(form, {
          id: row.id,
          name: row.name ?? '',
          // 使用 nullish 合并，避免把 0（根节点 id）当作 falsy 丢失
          parentId: row.parentId ?? null,
          leader: row.leader ?? '',
          phone: row.phone ?? '',
          email: row.email ?? '',
          // 保留 0 为合法排序值
          sort: row.sort ?? 0,
          // status 可能为 0/1/null，使用 ?? 保持原值或默认 '1'
          status: String(row.status ?? '1')
        })
        console.log('编辑模式 - 表单数据:', form)
        // 编辑时排除自己及其子节点，避免循环引用
        parentOptions.value = buildSelectOptions(rawTree.value, '', row.id)
        console.log('编辑模式 - 上级选项数量:', parentOptions.value.length)
      } else {
        dialogTitle.value = t('message.create_dept')
        // 确保新增时重置所有字段
        Object.assign(form, {
          id: null,
          name: '',
          parentId: null,
          leader: '',
          phone: '',
          email: '',
          sort: 0,
          status: '1'
        })
        console.log('新增模式 - 表单数据:', form)
        // 新增时始终使用当前 rawTree 构建完整的上级选项，避免重用编辑时被排除的列表
        parentOptions.value = buildSelectOptions(rawTree.value)
        // 如果仍然没有数据，尝试重新加载一次作为回退
        if (!parentOptions.value || parentOptions.value.length === 0) {
          console.warn('上级部门选项为空，尝试重新加载')
          load()
          parentOptions.value = parentOptions.value && parentOptions.value.length ? parentOptions.value : [ { value: null, label: '根部门' } ]
        }
      }
      dialogVisible.value = true
      try {
        formRef.value?.clearValidate?.()
      } catch (e) {}
    }

    async function onSave() {
      console.log('开始保存部门:', { formData: form, isEdit: !!form.id })
      if (!formRef.value) {
        console.error('表单引用不存在')
        return
      }
      const valid = await formRef.value.validate().catch(() => false)
      console.log('表单验证结果:', valid)
      if (!valid) return

      saving.value = true
      try {
        // 标准化字段类型，避免后端校验或更新失败
        const payload: any = {
          // 复制表单的其他字段
          name: form.name,
          leader: form.leader,
          phone: form.phone,
          email: form.email,
          // 将可能为字符串的数值字段转换为数字或 null
          sort: form.sort == null ? 0 : Number(form.sort),
          parentId: form.parentId == null ? null : Number(form.parentId),
          status: form.status == null ? 1 : (typeof form.status === 'string' ? Number(form.status) : form.status)
        }
        // 包含 id 表示编辑，否则为新增
        if (form.id != null && form.id !== '') payload.id = Number(form.id)

        console.log('调用API保存部门，payload:', payload)
  const res = await saveDept(payload)
  console.log('API调用成功，返回：', res)
  showApiResult(res, String(t('message.save_success')), () => { dialogVisible.value = false; load() })
      } catch (e: any) {
        console.error('保存失败:', e)
        // 后端返回的错误信息优先展示，使用可选链保护访问
        const msg = e?.response?.data?.message || e?.response?.data?.msg || String(t('message.save_failed'))
        ElMessage.error(String(msg))
      } finally {
        saving.value = false
      }
    }

    async function onDelete(row: any) {
      try {
        await ElMessageBox.confirm(`${row.name} ` + String(t('message.delete_confirm')), String(t('message.confirm')), { type: 'warning' })
  const res: any = await deleteDepts({ ids: [row.id] })
  showApiResult(res, String(t('message.delete_success')), () => load())
      } catch (e) {}
    }

    async function onStatusChange(row: any, newStatus: any) {
      console.log('准备更新状态:', { deptName: row.name, deptId: row.id, oldStatus: row.status, newStatus: newStatus })
      newStatus = String(newStatus)
      const action = newStatus === '1' ? String(t('message.activate')) : String(t('message.disable'))
      try {
        await ElMessageBox.confirm(`${action} ` + row.name + ' ? ', String(t('message.confirm')), { type: 'warning' })
        console.log('调用API更新状态:', { id: row.id, status: newStatus })
        const res: any = await changeDeptStatus({ id: row.id, status: newStatus })
        showApiResult(res, String(t('message.status_update_success')), () => {
          // 直接更新当前行的状态，避免重新加载整个列表
          row.status = newStatus
          console.log('状态更新完成:', { deptName: row.name, newStatus: row.status })
          // 更新 deptOptions 中的状态
          const deptOption = deptOptions.value.find(dept => dept.id === row.id)
          if (deptOption) {
            deptOption.status = newStatus
            console.log('上级部门选项状态已更新')
          }
        })
      } catch (e: any) {
        console.error('状态更新失败:', e)
        if (e && e === 'cancel') return
        const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.status_update_failed'))
        ElMessage.error(String(m))
      }
    }

    function handleSwitchChange(row: any, val: string | number | boolean) {
      if (ignoreStatusChange.value) return
      if (!userClicked.value) return
      userClicked.value = false
      onStatusChange(row, String(val))
    }

    function onSwitch(row: any, val: string | number | boolean) {
      console.log('状态切换:', { row: row.name, currentStatus: row.status, newValue: val, newValueType: typeof val })
      handleSwitchChange(row, val)
    }

    onMounted(() => {
      load()
    })

    
    return {
      filters,
      list,
      tableLoading,
      dialogVisible,
      dialogTitle,
      form,
      formRef,
      rules,
      saving,
      onSearch,
      onReset,
      openDialog,
      onSave,
      onDelete,
      onStatusChange,
      handleSwitchChange,
      onSwitch,
      onSwitchChange: handleSwitchChange,
      userClicked,
      ignoreStatusChange,
      deptTable,
      isExpandAll,
      expandedKeys,
      expandAll,
      collapseAll,
      deptOptions,
      parentOptions,
      canCreate,
      canEdit,
      canDelete
    }
  }
})
</script>

<style scoped>
.search-row { z-index: 2 }
.dept-card { min-height: 0 }
.dept-card .el-card__body { display:flex; flex-direction:column }
.tree-wrap { max-height: 60vh; overflow:auto }
.tree-row { display:flex; justify-content:space-between; align-items:center; padding:6px 8px }
.tree-row-left { display:flex; flex-direction:column }
.dept-name { font-weight:500 }
.dept-path { color:#999; font-size:12px }
.tree-row-actions { display:flex; gap:8px; align-items:center }
.name-cell { display:inline-flex; align-items:center; gap:8px }
</style>
