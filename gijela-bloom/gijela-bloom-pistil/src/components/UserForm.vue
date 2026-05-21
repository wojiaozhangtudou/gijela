<template>
  <el-form :model="model" ref="formRef" :rules="rules">
    <el-form-item :label="$t('message.username')" prop="username">
      <el-input v-model="model.username" />
    </el-form-item>
    <template v-if="!model.id">
      <el-form-item :label="$t('message.password')" prop="password">
        <el-input v-model="model.password" show-password />
      </el-form-item>
      <el-form-item :label="$t('message.confirm_password')" prop="confirmPassword">
        <el-input v-model="model.confirmPassword" show-password />
      </el-form-item>
    </template>
    <el-form-item :label="$t('message.nickname')" prop="nickname">
      <el-input v-model="model.nickname" />
    </el-form-item>
    <el-form-item :label="$t('message.email')" prop="email">
      <el-input v-model="model.email" />
    </el-form-item>
    <el-form-item :label="$t('message.phone')" prop="phone">
      <el-input v-model="model.phone" />
    </el-form-item>
    <el-form-item :label="$t('message.status')" prop="status">
      <el-switch v-model="model.status" active-value="1" inactive-value="0" :active-text="$t('message.status_active')" :inactive-text="$t('message.status_disabled')" />
    </el-form-item>
    <el-form-item :label="$t('message.roles')" prop="roleIds">
      <el-select v-model="model.roleIds" multiple filterable clearable :placeholder="t('message.select_roles')">
        <el-option v-for="opt in rolesOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <div style="font-size:12px;color:#999;margin-top:4px">{{ t('message.roles_count', { count: rolesOptions.length }) }}</div>
    </el-form-item>

    <el-form-item :label="$t('message.dept')" prop="deptId">
      <el-select v-model="model.deptId" clearable :placeholder="t('message.select_dept')">
        <el-option v-for="opt in parentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <div style="font-size:12px;color:#999;margin-top:4px">{{ t('message.parent_options_count', { count: parentOptions.length }) }}</div>
    </el-form-item>

    <el-form-item :label="$t('message.posts')" prop="postIds">
      <el-select v-model="model.postIds" multiple filterable clearable :placeholder="t('message.select_posts')">
        <el-option v-for="opt in postsOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <div style="font-size:12px;color:#999;margin-top:4px">{{ t('message.posts_count', { count: postsOptions.length }) }}</div>
    </el-form-item>
  </el-form>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pageRoles } from '../api/role'
import { pagePosts } from '../api/post'
import { fetchDeptSelectTree } from '../api/dept'
import { ElMessage } from 'element-plus'

export default defineComponent({
  props: {
    model: { type: Object, required: true },
    rules: { type: Object, default: () => ({}) }
  },
  setup() {
    const formRef = ref()
    const rolesOptions = ref<any[]>([])
    const postsOptions = ref<any[]>([])
    const parentOptions = ref<any[]>([])

    function buildSelectOptions(nodes: any[], prefix = ''): any[] {
      const out: any[] = []
      for (const n of nodes) {
        out.push({ value: n.id, label: prefix + (n.name || '') })
        if (n.children && n.children.length) out.push(...buildSelectOptions(n.children, prefix + '└─ '))
      }
      return out
    }

    async function loadOptions() {
      // helper: fetch all pages with pageSize <= 200
      async function fetchAllPages(fetchFn: any, pageSize = 200) {
        const out: any[] = []
        let current = 1
        while (true) {
          const resp = await fetchFn({ current, size: pageSize })
          let records: any[] = []
          if (resp && resp.data) {
            const d = resp.data
            if (Array.isArray(d.records)) records = d.records
            else if (Array.isArray(d)) records = d
            else if (Array.isArray((d as any).list)) records = (d as any).list
            // If API returns total + records, we can break early when collected all
            const total = d.total || (Array.isArray(d.records) ? d.records.length : null)
            out.push(...records)
            if (records.length < pageSize) break
            if (total != null && out.length >= total) break
          } else if (Array.isArray(resp)) {
            out.push(...resp)
            break
          } else {
            break
          }
          current += 1
        }
        return out
      }

      // Roles (paged)
      try {
        const roles = await fetchAllPages(pageRoles, 200)
        console.debug('[UserForm] loaded roles count=', roles.length)
        rolesOptions.value = roles.map((x: any) => ({ value: x.id, label: x.name || x.roleName || x.title || String(x.id) }))
        if (rolesOptions.value.length === 0) ElMessage.info(String(t('message.load_roles_failed')))
      } catch (e: any) {
        console.warn('load roles failed', e)
        ElMessage.error(String(t('message.load_roles_failed')))
      }

      // Posts
      // Posts (paged)
      try {
        const posts = await fetchAllPages(pagePosts, 200)
        console.debug('[UserForm] loaded posts count=', posts.length)
        postsOptions.value = posts.map((x: any) => ({ value: x.id, label: x.name || x.title || String(x.id) }))
        if (postsOptions.value.length === 0) ElMessage.info(String(t('message.load_posts_failed')))
      } catch (e: any) {
        console.warn('load posts failed', e)
        ElMessage.error(String(t('message.load_posts_failed')))
      }

      // Depts
      try {
        const r3 = await fetchDeptSelectTree()
        const tree = (r3 && r3.data) ? r3.data : (Array.isArray(r3) ? r3 : [])
        parentOptions.value = buildSelectOptions(tree)
        if (!parentOptions.value || parentOptions.value.length === 0) ElMessage.info(String(t('message.load_depts_failed')))
      } catch (e: any) {
        console.warn('load depts failed', e)
        ElMessage.error(String(t('message.load_depts_failed')))
      }
    }

  const { t } = useI18n()
  onMounted(() => { loadOptions() })
    function validate(cb?: (valid: boolean) => void) {
        if (!formRef.value) return cb ? cb(false) : Promise.resolve(false)
        return (formRef.value as any).validate(cb)
      }
  function resetFields() { if (formRef.value) (formRef.value as any).resetFields() }
    return { formRef, validate, resetFields, rolesOptions, postsOptions, parentOptions, t }
  }
})
</script>