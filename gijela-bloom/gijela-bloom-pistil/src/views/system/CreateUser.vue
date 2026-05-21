<template>
  <div>
    <el-card>
  <h3>{{ $t('message.create_user') }}</h3>
  <UserForm :model="form" :rules="rules" ref="formRef" />
      <div style="margin-top:12px">
  <el-button @click="onCancel">{{ $t('message.cancel') }}</el-button>
  <el-button type="primary" :loading="saving" @click="onSave">{{ $t('message.save') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref } from 'vue'
import UserForm from '../../components/UserForm.vue'
import { saveUser, assignUserDepts, assignUserPosts } from '../../api/user'
import { ElMessage } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

export default defineComponent({
  components: { UserForm },
  setup() {
    const router = useRouter()
    const { t } = useI18n()
  const form = reactive({ username: '', password: '', confirmPassword: '', nickname: '', email: '', phone: '', status: '1', roleIds: [], postIds: [], deptId: null, deptIds: [] as number[] })
    const saving = ref(false)
    const formRef = ref()
  const rules = {
      username: [{ required: true, message: String(t('validation.enter_username')), trigger: 'blur' }],
  nickname: [{ required: true, message: String(t('validation.enter_fullname')), trigger: 'blur' }],
      password: [
        { required: true, message: String(t('validation.enter_password')), trigger: 'blur' },
        { min: 6, message: String(t('validation.password_min_6')), trigger: 'blur' }
      ]
      ,
      confirmPassword: [
        { required: true, message: String(t('validation.enter_confirm_password')), trigger: 'blur' },
        { validator: (rule: any, value: string, callback: any) => {
            if (value !== form.password) {
              callback(new Error(String(t('validation.password_mismatch'))))
            } else {
              callback()
            }
          }, trigger: 'blur' }
      ]
    }

    async function onSave() {
      if (formRef.value) {
        (formRef.value as any).validate(async (valid: boolean) => {
          if (!valid) return
          saving.value = true
          try {
            const res = await saveUser(form)
            showApiResult(res, String(t('message.create_success')), async () => {
              const api = (res && (res as any).data) ? (res as any).data : res
              const newId = (api && api.data && api.data.id) ? api.data.id : (api && api.id ? api.id : null)
              try {
                if (newId) {
                  if (Array.isArray(form.deptIds) && form.deptIds.length > 0) await assignUserDepts(newId, form.deptIds)
                  if (Array.isArray(form.postIds) && form.postIds.length > 0) await assignUserPosts(newId, form.postIds)
                }
              } catch (assocErr: any) {
                console.warn('assign depts/posts failed after create', assocErr)
                const assocMsg = assocErr && assocErr.response && (assocErr.response.data?.message || assocErr.response.data?.msg) ? (assocErr.response.data.message || assocErr.response.data.msg) : String(t('message.create_success') + '，但设置部门/岗位关联失败')
                ElMessage.error(String(assocMsg))
                router.push('/system/users')
                return
              }
              router.push('/system/users')
            })
          } catch (e) {
            ElMessage.error(String(t('message.create_failed')))
          } finally { saving.value = false }
        })
      }
    }

    function onCancel() { router.back() }
    return { form, saving, formRef, rules, onSave, onCancel }
  }
})
</script>
