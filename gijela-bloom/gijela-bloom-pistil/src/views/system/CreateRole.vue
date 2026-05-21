<template>
  <div>
    <el-card>
      <h3>{{ $t('message.create_role') }}</h3>
  <RoleForm :model="form" :rules="rules" ref="formRef" />
      <div style="margin-top:12px">
        <el-button @click="onCancel">{{ $t('message.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('message.save') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref } from 'vue'
import RoleForm from './RoleForm.vue'
import { saveRole } from '../../api/role'
import { ElMessage } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

export default defineComponent({
  components: { RoleForm },
  setup() {
    const router = useRouter()
    const { t } = useI18n()
  const form = reactive({ name: '', code: '', status: '1' })
    const saving = ref(false)
  const formRef = ref<any>()
    const rules = { name: [{ required: true, message: String(t('validation.enter_role_name')), trigger: 'blur' }] }

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
            showApiResult(res, String(t('message.create_success')), async () => {
              const api = (res && (res as any).data) ? (res as any).data : res
              const extractRoleId = (obj: any) => {
                if (!obj) return null
                if (obj.data != null) {
                  const d = obj.data
                  if (typeof d === 'number' || typeof d === 'string') return String(d)
                  if (d.id != null) return String(d.id)
                  if (d.roleId != null) return String(d.roleId)
                }
                if (obj.id != null) return String(obj.id)
                return null
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
            const m = e && e.response && (e.response.data?.message || e.response.data?.msg) ? (e.response.data.message || e.response.data.msg) : String(t('message.create_failed'))
            ElMessage.error(String(m))
          } finally { saving.value = false }
        })
      }
    }

    function onCancel() { router.back() }
    return { form, saving, formRef, rules, onSave, onCancel }
  }
})
</script>
