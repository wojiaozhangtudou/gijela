<template>
  <div>
    <el-form :model="model" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item :label="$t('message.role_name')" prop="name">
        <el-input v-model="model.name" />
      </el-form-item>
      <el-form-item :label="$t('message.code')" prop="code">
        <el-input v-model="model.code" />
      </el-form-item>
      <el-form-item :label="$t('message.status')" prop="status">
        <el-switch v-model="model.status" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" />
      </el-form-item>
      <el-form-item :label="$t('message.menu_management')">
        <el-tree
          v-loading="menusLoading"
          :data="menuTree"
          show-checkbox
          node-key="id"
            :props="treeProps"
          :default-expanded-keys="expandedKeys"
          :default-checked-keys="checkedMenuIds"
          ref="treeRef"
          style="border:1px solid #eee;padding:8px;border-radius:4px;max-height:300px;overflow:auto"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts">
import { defineComponent, toRefs, ref, onMounted, watch } from 'vue'
import { getMenuTree } from '../../api/menu'
import { getRoleMenus } from '../../api/role'

export default defineComponent({
  props: {
    model: { type: Object, required: true },
  rules: { type: Object, required: false },
  // when true, only send leaf node ids as permissions; when false, send full path ids (checked + ancestors)
  sendLeafOnly: { type: Boolean, required: false, default: false }
  },
  setup(props) {
    const formRef = ref()
    const treeRef = ref()
    const menuTree = ref<any[]>([])
    const checkedMenuIds = ref<string[]>([])
    const expandedKeys = ref<string[]>([])
    const menusLoading = ref(false)
    const treeProps = { label: 'name', children: 'children' }
  const idNodeMap = ref<Record<string, any>>({})
  const parentMap = ref<Record<string, string>>({})

    function buildTree(flat: any[]): any[] {
      if (!Array.isArray(flat)) return []
      const map: Record<string, any> = {}
      flat.forEach(it => { const idStr = it.id != null ? String(it.id) : ''; if (idStr) map[idStr] = { ...it, id: idStr, children: [] } })
      const roots: any[] = []
      flat.forEach(it => {
        const pid = it.parentId || it.parent_id || it.parent || null
        const pidStr = pid != null ? String(pid) : ''
        const idStr = it.id != null ? String(it.id) : ''
        if (pidStr && map[pidStr]) {
          if (idStr && map[idStr]) map[pidStr].children.push(map[idStr])
        } else if (idStr && map[idStr]) {
          roots.push(map[idStr])
        }
      })
      return roots
    }

    async function loadMenus() {
      menusLoading.value = true
      try {
        const r = await getMenuTree()
        const data = (r && (r as any).data) ? (r as any).data : r
        let tree = Array.isArray(data) ? data : []
        const flatLike = tree.every((n: any) => !n.children || n.children.length === 0) && tree.some((n: any) => n.parentId || n.parent_id)
        if (flatLike) tree = buildTree(tree)
        // normalize ids to strings across the tree
        function normalizeIds(nodes: any[]) {
          nodes.forEach(n => {
            if (n.id != null) n.id = String(n.id)
            if (n.children && n.children.length) normalizeIds(n.children)
          })
        }
        normalizeIds(tree)
        menuTree.value = tree
        // build id->node map and parent map for ancestor checks
        const map: Record<string, any> = {}
        const pmap: Record<string, string> = {}
        function buildMap(nodes: any[], parentId?: string) {
          nodes.forEach(n => {
            const id = String(n.id)
            map[id] = n
            if (parentId) pmap[id] = parentId
            if (n.children && n.children.length) buildMap(n.children, id)
          })
        }
        buildMap(tree)
        idNodeMap.value = map
        parentMap.value = pmap
        expandedKeys.value = tree.map(n => String(n.id)).slice(0, 30)
      } finally { menusLoading.value = false }
    }

    async function loadRoleMenus() {
      if (!props.model || !props.model.id) return
      try {
        const r = await getRoleMenus(String(props.model.id))
  const data = (r && (r as any).data) ? (r as any).data : r
  // backend might return array of ids or object { menuIds: [] } or nested shapes
  const ids = Array.isArray(data) ? data : (data?.menuIds || data?.data || [])
  const idsStr = (ids || []).map((x: any) => String(x))
  // For UI rendering, only mark leaf nodes as checked so checking a parent doesn't auto-check all children
  const leafOnly = idsStr.filter((id: string) => {
    const node = idNodeMap.value && idNodeMap.value[id]
    return node ? !(node.children && node.children.length) : true
  })
  // store UI checked keys as leaf-only to avoid cascading checks; keep full ids as needed
  checkedMenuIds.value = leafOnly
        setTimeout(() => {
          if (treeRef.value) try { (treeRef.value as any).setCheckedKeys(checkedMenuIds.value, false) } catch {}
        }, 50)
      } catch {}
    }

    function getCheckedMenuIds(): string[] {
      if (treeRef.value) {
  const keys = (treeRef.value as any).getCheckedKeys(false) || []
  const half = (treeRef.value as any).getHalfCheckedKeys?.() || []
  // ensure returned ids are strings
  return [...new Set([...keys, ...half])].map((k: any) => String(k))
      }
      return checkedMenuIds.value
    }

    function collectPermissions() {
      const all = getCheckedMenuIds()
      if (props.sendLeafOnly) {
        // filter to only leaf nodes (no children)
        const leafIds = all.filter((id: string) => {
          const node = idNodeMap.value && idNodeMap.value[id]
          return node ? !(node.children && node.children.length) : true
        })
        return { menuIds: leafIds }
      }
      // include ancestors for each selected id to form full path
      const result = new Set<string>(all)
      all.forEach((id: string) => {
        let p = parentMap.value && parentMap.value[id]
        while (p) { result.add(p); p = parentMap.value && parentMap.value[p] }
      })
      return { menuIds: Array.from(result) }
    }

    onMounted(async () => { await loadMenus(); await loadRoleMenus() })
    watch(() => props.model.id, () => { loadRoleMenus() })

    function validate(cb?: (valid: boolean) => void) {
      if (!formRef.value) return cb ? cb(false) : Promise.resolve(false)
      return (formRef.value as any).validate(cb)
    }
    function resetFields() { if (formRef.value) (formRef.value as any).resetFields() }
  return { ...toRefs(props), formRef, validate, resetFields, treeRef, menuTree, treeProps, checkedMenuIds, expandedKeys, menusLoading, collectPermissions }
  }
})
</script>
