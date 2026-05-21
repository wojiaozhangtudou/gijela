<template>
  <div>
    <el-card class="menu-card">
      <div class="search-row" style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
        <el-input v-model="filters.name" :placeholder="$t('message.menu_name')" style="width:280px" />
        <div style="margin-left:auto">
          <el-button size="mini" @click="expandAll">{{ $t('message.expand_all') || '展开全部' }}</el-button>
          <el-button size="mini" @click="collapseAll">{{ $t('message.collapse_all') || '折叠全部' }}</el-button>
          <el-button type="primary" @click="onSearch">{{ $t('message.search') }}</el-button>
          <el-button @click="onReset">{{ $t('message.reset') }}</el-button>
          <el-button v-if="canCreate" type="primary" @click="openDialog()">{{ $t('message.create') }}</el-button>
        </div>
      </div>

  <el-table ref="menuTable" :data="list" stripe style="width:100%" v-loading="tableLoading" row-key="id" :tree-props="{ children: 'children' }" :default-expand-all="isExpandAll" :expand-row-keys="expandedKeys">
        <el-table-column prop="name" :label="$t('message.name')">
          <template #default="{ row }">
            <div class="name-cell">
              <el-icon v-if="row.icon" :size="16" style="margin-right: 8px;">
                <component :is="row.icon" />
              </el-icon>
              <span class="menu-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.type==='D'" size="small">目录</el-tag>
            <el-tag v-else-if="row.type==='M'" type="success" size="small">菜单</el-tag>
            <el-tag v-else-if="row.type==='B'" type="warning" size="small">按钮</el-tag>
            <el-tag v-else-if="row.type==='S'" type="info" size="small">外链</el-tag>
            <el-tag v-else size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="path" :label="$t('message.path')">
          <template #default="{ row }">
            <div style="color:#999;font-size:12px">{{ row.path || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('message.status')" width="140">
          <template #default="{ row }">
            <div class="status-cell"><el-switch class="status-switch" :model-value="row.status" @mousedown="() => (userClicked = true)" @change="onSwitch(row, $event)" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" /></div>
          </template>
        </el-table-column>
  <el-table-column prop="sort" :label="$t('message.sort')" width="100" />
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
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('message.type')" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" @change="onTypeChange">
            <el-option label="目录" value="D" />
            <el-option label="菜单" value="M" />
            <el-option label="按钮" value="B" />
            <el-option label="外链" value="S" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="showPathField" :label="$t('message.path')" prop="path">
          <el-input v-model="form.path" :placeholder="pathPlaceholder" />
          <div class="helper-text">{{ pathHelper }}</div>
        </el-form-item>
        <el-form-item v-if="form.type === 'B'" label="权限标识" prop="permission">
          <el-input v-model="form.permission" placeholder="如: user:add" />
        </el-form-item>
          <el-form-item :label="$t('message.parent')" prop="parentId">
            <el-select v-model="form.parentId" clearable placeholder="--">
              <el-option v-for="opt in parentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <div style="font-size:12px;color:#999;margin-top:4px">
              共 {{ parentOptions.length }} 个上级菜单选项
            </div>
          </el-form-item>
        <el-form-item :label="$t('message.icon')" prop="icon">
          <div class="icon-selector">
            <div class="icon-grid">
              <div 
                v-for="icon in iconOptions" 
                :key="icon.value"
                class="icon-item"
                :class="{ 'selected': form.icon === icon.value }"
                @click="form.icon = form.icon === icon.value ? '' : icon.value"
              >
                <el-icon :size="24">
                  <component :is="icon.value" />
                </el-icon>
                <div class="icon-label">{{ icon.label }}</div>
              </div>
            </div>
            <div v-if="form.icon" class="selected-icon-info">
              <span>已选择: {{ iconOptions.find(i => i.value === form.icon)?.label }}</span>
              <el-button size="small" @click="form.icon = ''">清除</el-button>
            </div>
          </div>
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
import { saveMenu, deleteMenusApi, changeMenuStatus } from '../../api/menu'
import { fetchMenuTree } from '../../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useI18n } from 'vue-i18n'
import { hasPermission } from '../../utils/permission'
import { 
  House, User, Setting, Menu, Document, Folder, List, Grid, 
  Plus, Edit, Delete, Search, Star, Share, Download, 
  Upload, Phone, Calendar, Clock, Key, Lock
} from '@element-plus/icons-vue'

export default defineComponent({
  components: {
    House, User, Setting, Menu, Document, Folder, List, Grid, 
    Plus, Edit, Delete, Search, Star, Share, Download, 
    Upload, Phone, Calendar, Clock, Key, Lock
  },
  setup() {
    const { t } = useI18n()
    const filters = reactive({ name: '' })

  // list state for tree view
  const list = ref<any[]>([])
  const rawTree = ref<any[]>([])
  const tableLoading = ref(false)
  const expandedKeys = ref<any[]>([])
  const menuTable = ref<any>(null)
  const isExpandAll = ref(false)
  const parentOptions = ref<any[]>([])
  const iconOptions = ref<any[]>([
    { value: 'House', label: '首页' },
    { value: 'User', label: '用户' },
    { value: 'Key', label: '密码' },
    { value: 'Lock', label: '权限' },
    { value: 'Setting', label: '设置' },
    { value: 'Menu', label: '菜单' },
    { value: 'Document', label: '文档' },
    { value: 'Folder', label: '文件夹' },
    { value: 'List', label: '列表' },
    { value: 'Grid', label: '网格' },
    { value: 'Plus', label: '添加' },
    { value: 'Edit', label: '编辑' },
    { value: 'Delete', label: '删除' },
    { value: 'Search', label: '搜索' },
    { value: 'Star', label: '收藏' },
    { value: 'Share', label: '分享' },
    { value: 'Download', label: '下载' },
    { value: 'Upload', label: '上传' },
    { value: 'Phone', label: '电话' },
    { value: 'Calendar', label: '日历' },
    { value: 'Clock', label: '时钟' }
  ])

  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const form = reactive<any>({ id: null, name: '', path: '', parentId: null, status: '1', type: 'M', icon: '', permission: '' })
    const saving = ref(false)
    const formRef = ref()
  const rules = { 
    name: [{ required: true, message: String(t('validation.enter_menu_name')), trigger: 'blur' }],
    type: [{ required: true, message: String(t('validation.select_menu_type')), trigger: 'change' }],
    path: [
      { validator: (_: any, val: string, cb: any) => {
          if (form.type === 'M' || form.type === 'S') {
            if (!val) return cb(new Error('请输入路径'))
          }
          if (form.type === 'M') {
            if (!val.startsWith('/')) return cb(new Error('菜单路径需以 / 开头'))
          }
          if (form.type === 'S') {
            const ok = /^(https?:\/\/[^\s]+|\/[a-zA-Z0-9_\/-]*)$/.test(val)
            if (!ok) return cb(new Error('外链需以 / 或 http(s):// 开头'))
          }
          cb()
        }, trigger: 'blur' }
    ],
    permission: [
      { validator: (_: any, val: string, cb: any) => {
          if (form.type === 'B' && !val) return cb(new Error('按钮权限标识必填'))
          cb()
        }, trigger: 'blur' }
    ]
  }

    const userClicked = ref(false)

    // Try to fetch a full tree first (used elsewhere). We'll only render top-level nodes initially by
    // setting children to [] for them so el-tree will call loadNode when expanded (lazy).
  function onSearch() { load() }
  function onReset() { filters.name = ''; onSearch() }

    function openDialog(row?: any) {
      if (row) { 
        dialogTitle.value = t('message.edit_menu'); 
        Object.assign(form, row);
        // 编辑时排除自己及其子节点
        parentOptions.value = buildSelectOptions(rawTree.value, '', row.id);
      } else { 
        dialogTitle.value = t('message.create_menu'); 
        Object.assign(form, { id: null, name: '', path: '', parentId: null, status: '1', type: 'M', icon: '', permission: '' });
        // 新增时显示所有可选项
        parentOptions.value = buildSelectOptions(rawTree.value);
      }
      dialogVisible.value = true
      try { formRef.value?.clearValidate?.() } catch (e) {}
    }

    function onTypeChange() {
      // 重置不适用字段
      if (form.type === 'B') {
        form.path = ''
      }
      if (form.type !== 'B') {
        form.permission = ''
      }
    }

  

    async function onSave() {
  if (!formRef.value) { 
      saving.value = true; 
      try { 
        const res: any = await saveMenu(form)
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
          const res: any = await saveMenu(form)
          showApiResult(res, String(t('message.save_success')), () => { dialogVisible.value = false; load() })
        } catch (e) {
          ElMessage.error(String(t('message.save_failed')))
        } finally { saving.value = false }
      })
    }

    async function onDelete(row: any) {
      try {
        await ElMessageBox.confirm(`${row.name} ` + String(t('message.delete_confirm')), String(t('message.confirm')), { type: 'warning' })
  const res: any = await deleteMenusApi({ ids: [row.id] })
  showApiResult(res, String(t('message.delete_success')), () => load())
      } catch (e) {}
    }

    async function onSwitch(row: any, val: any) {
      if (!userClicked.value) return
      userClicked.value = false
      const newStatus = String(val)
      try {
        await ElMessageBox.confirm((newStatus === '1' ? String(t('message.activate')) : String(t('message.disable'))) + ' ' + row.name + ' ?', String(t('message.confirm')), { type: 'warning' })
        const res: any = await changeMenuStatus({ id: row.id, status: newStatus })
        showApiResult(res, String(t('message.status_update_success')), () => {
          row.status = newStatus
          load()
        })
      } catch (e: any) {
        const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.status_update_failed'))
        ElMessage.error(String(m))
      }
    }

    // normalize a node: ensure status is string and children exist
    function normalizeNode(n: any) {
      const children = n.children && Array.isArray(n.children) ? n.children.map(normalizeNode) : []
      return { ...n, status: n.status == null ? '0' : String(n.status), children }
    }

    function buildSelectOptions(nodes: any[], prefix = '', excludeId?: string): any[] {
      const out: any[] = []
      for (const n of nodes) {
        // 排除指定的节点及其子节点（用于编辑时避免循环引用）
        if (excludeId && n.id === excludeId) continue
        
        // 只包含目录(D)和菜单(M)，排除按钮(B)
        if (n.type === 'D' || n.type === 'M') {
          out.push({ value: n.id, label: prefix + (n.name || '') })
          if (n.children && n.children.length) out.push(...buildSelectOptions(n.children, prefix + '└─ ', excludeId))
        }
      }
      return out
    }

    function filterTree(nodes: any[], name: string): any[] {
      if (!name) return nodes
      const q = name.toLowerCase()
      function helper(node: any): any | null {
        const matched = (node.name || '').toString().toLowerCase().includes(q)
        const children = (node.children || []).map(helper).filter((c: any) => c)
        if (matched || children.length) return { ...node, children }
        return null
      }
      return nodes.map(helper).filter((n: any) => n)
    }

    async function load() {
      tableLoading.value = true
      try {
        const r = await fetchMenuTree()
        const nodes = (r && Array.isArray(r) ? r : []) as any[]
        rawTree.value = nodes.map(normalizeNode)
        list.value = filterTree(rawTree.value, filters.name)
        parentOptions.value = buildSelectOptions(rawTree.value)
      } catch (e) { ElMessage.error(String(t('message.load_menus_failed'))) } finally { tableLoading.value = false }
    }

    function collectIds(nodes: any[], out: any[] = []) {
      for (const n of nodes) {
        if (n && n.id != null) out.push(n.id)
        if (n.children && n.children.length) collectIds(n.children, out)
      }
      return out
    }

    function flattenNodes(nodes: any[], out: any[] = []) {
      for (const n of nodes) {
        if (n) out.push(n)
        if (n.children && n.children.length) flattenNodes(n.children, out)
      }
      return out
    }

    function expandAll() {
      isExpandAll.value = true
      const ids = collectIds(list.value)
      expandedKeys.value = [...ids]
      console.log('expandAll: setting isExpandAll=true, expandedKeys to', ids)
    }

    function collapseAll() {
      isExpandAll.value = false
      expandedKeys.value = []
      console.log('collapseAll: setting isExpandAll=false, clearing expandedKeys')
    }
    const canCreate = computed(() => hasPermission('menu:create'))
    const canEdit = computed(() => hasPermission('menu:update'))
    const canDelete = computed(() => hasPermission('menu:delete'))
    const showPathField = computed(() => form.type === 'M' || form.type === 'S')
    const pathPlaceholder = computed(() => form.type === 'S' ? '例如 /chat 或 https://example.com' : '以 / 开头的内部路由')
    const pathHelper = computed(() => {
      if (form.type === 'S') return '外链：/chat（同域网关）或 https:// 域名。'
      if (form.type === 'M') return '内部路由：例如 /system/user'
      return ''
    })

  onMounted(() => { load() })

  return { filters, list, tableLoading, dialogVisible, dialogTitle, form, formRef, rules, saving, onSearch, onReset, openDialog, onSave, onDelete, onSwitch, userClicked, expandedKeys, expandAll, collapseAll, menuTable, isExpandAll, parentOptions, iconOptions, canCreate, canEdit, canDelete, showPathField, pathPlaceholder, pathHelper, onTypeChange }
  }
})
</script>

<style scoped>
.search-row { z-index: 2 }
.menu-card { min-height: 0 }
.menu-card .el-card__body { display:flex; flex-direction:column }
.tree-wrap { max-height: 60vh; overflow:auto }
.tree-row { display:flex; justify-content:space-between; align-items:center; padding:6px 8px }
.tree-row-left { display:flex; flex-direction:column }
.menu-name { font-weight:500 }
.menu-path { color:#999; font-size:12px }
.tree-row-actions { display:flex; gap:8px; align-items:center }
.name-cell { display:inline-flex; align-items:center; gap:8px }

.icon-selector {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background: #fafbfc;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 6px;
  margin-bottom: 8px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4px 2px;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: white;
  min-height: 42px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}

.icon-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.icon-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.icon-item .el-icon {
  margin-bottom: 2px;
  color: #606266;
  transition: color 0.2s ease;
}

.icon-item.selected .el-icon {
  color: #409eff;
}

.helper-text { font-size:12px; color:#888; margin-top:4px }

.icon-label {
  font-size: 10px;
  color: #606266;
  text-align: center;
  line-height: 1.1;
  word-break: break-word;
  font-weight: 400;
  transition: color 0.2s ease;
}

.icon-item.selected .icon-label {
  color: #409eff;
  font-weight: 500;
}

.selected-icon-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border: 1px solid #b3e5fc;
  border-radius: 6px;
  font-size: 14px;
  color: #1976d2;
  font-weight: 500;
}
</style>
