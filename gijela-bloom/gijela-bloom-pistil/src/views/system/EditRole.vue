<template>
  <div>
    <el-card>
      <h3>{{ $t('message.edit_role') }}</h3>
  <RoleForm :model="form" :rules="rules" ref="formRef" />
      <div style="margin-top:12px">
        <el-button @click="onCancel">{{ $t('message.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('message.save') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref, onMounted } from 'vue'
import RoleForm from './RoleForm.vue'
import { getRoleById, saveRole } from '../../api/role'
import { ElMessage } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

export default defineComponent({
  components: { RoleForm },
  setup() {
    const { t } = useI18n()
    const router = useRouter()
    const route = useRoute()
    const id = route.params.id
  const form = reactive<any>({ id: null, name: '', code: '', status: '1' })
    const saving = ref(false)
  const formRef = ref<any>()
    const rules = { name: [{ required: true, message: String(t('validation.enter_role_name')), trigger: 'blur' }] }

    async function load() {
      if (id) {
        const r = await getRoleById(String(id))
        const d = r?.data || r
  Object.assign(form, d)
  // normalize status to string for consistent binding with el-switch (active-value='1', inactive-value='0')
  try { form.status = d && d.status != null ? String(d.status) : '0' } catch { form.status = '0' }
      }
    }

    async function onSave() {
      if (formRef.value) {
        (formRef.value as any).validate(async (valid: boolean) => {
          if (!valid) return
          saving.value = true
          try {
            let payload: any = { ...form }
            if (formRef.value && formRef.value.collectPermissions) {
              Object.assign(payload, formRef.value.collectPermissions())
            }
            const res = await saveRole(payload)
            showApiResult(res, String(t('message.save_success')), async () => {
              const api = (res && (res as any).data) ? (res as any).data : res
              const extractRoleId = (obj: any) => {
                if (!obj) return (payload && payload.id) ? String(payload.id) : null
                if (obj.data != null) {
                  const d = obj.data
                  if (typeof d === 'number' || typeof d === 'string') return String(d)
                  if (d.id != null) return String(d.id)
                  if (d.roleId != null) return String(d.roleId)
                }
                if (obj.id != null) return String(obj.id)
                return (payload && payload.id) ? String(payload.id) : null
              }
              const roleId = extractRoleId(api)
              if (formRef.value && formRef.value.collectPermissions && roleId) {
                try {
                  const perms = formRef.value.collectPermissions()
                  await (await import('../../api/role')).assignRoleMenus({ roleId: String(roleId), menuIds: perms.menuIds || [] })
                } catch (e) { console.debug('assignRoleMenus failed', e) }
              }
              router.push('/sys/roles')
            })
          } catch (e: any) {
            const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.save_failed'))
            ElMessage.error(String(m))
          } finally { saving.value = false }
        })
      }
    }

    function onCancel() { router.back() }
    onMounted(() => { load() })
    return { form, saving, formRef, rules, onSave, onCancel }
  }
})
</script>
