<template>
  <div>
    <el-card>
  <h3>{{ $t('message.edit_user') }}</h3>
      <UserForm :model="form" :rules="rules" ref="formRef" />
      <div style="margin-top:12px">
  <el-button @click="onCancel">{{ $t('message.cancel') }}</el-button>
  <el-button type="primary" :loading="saving" @click="onSave">{{ $t('message.save') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref, onMounted } from 'vue'
import UserForm from '../../components/UserForm.vue'
import { getUserById, saveUser, assignUserDepts, assignUserPosts } from '../../api/user'
import { ElMessage } from 'element-plus'
import { showApiResult } from '../../utils/apiResult'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

export default defineComponent({
  components: { UserForm },
  setup() {
  const { t } = useI18n()
    const router = useRouter()
    const route = useRoute()
    const id = route.params.id
  const form = reactive<any>({ id: null, username: '', nickname: '', email: '', phone: '', status: '1', roleIds: [], postIds: [], deptId: null, deptIds: [] })
    const saving = ref(false)
    const formRef = ref()
  const rules = {
      username: [{ required: true, message: String(t('validation.enter_username')), trigger: 'blur' }],
  nickname: [{ required: true, message: String(t('validation.enter_fullname')), trigger: 'blur' }],
  // password is not editable from admin edit page; change password in personal profile only
    }

    async function load() {
      if (id) {
        const r = await getUserById(String(id))
        const d = r?.data || r
  // merge returned data but do not populate password fields
    Object.assign(form, d)
      // normalize status to string so el-switch (active-value='1') works consistently
      form.status = d && d.status != null ? String(d.status) : String(form.status || '0')
      form.password = ''
      form.confirmPassword = ''
      // normalize relation fields
    form.roleIds = (d && d.roleIds && Array.isArray(d.roleIds)) ? d.roleIds.map((x: any) => Number(x)) : (d && d.roles ? d.roles.map((x: any) => Number(x.id || x)) : [])
    form.postIds = (d && d.postIds && Array.isArray(d.postIds)) ? d.postIds.map((x: any) => Number(x)) : []
    form.deptId = d && (d.deptId != null ? Number(d.deptId) : (d.parentId != null ? Number(d.parentId) : null))
    form.deptIds = (d && d.deptIds && Array.isArray(d.deptIds)) ? d.deptIds.map((x: any) => Number(x)) : []
      }
    }

    async function onSave() {
      if (formRef.value) {
        (formRef.value as any).validate(async (valid: boolean) => {
          if (!valid) return
          saving.value = true
            try {
                // prepare payload from form (password managed in personal profile)
                const payload = { ...form }
                const res = await saveUser(payload)
                showApiResult(res, String(t('message.save_success')), async () => {
                  try {
                    if (Array.isArray(form.deptIds) && form.deptIds.length > 0) {
                      await assignUserDepts(form.id, form.deptIds)
                    }
                    if (Array.isArray(form.postIds) && form.postIds.length > 0) {
                      await assignUserPosts(form.id, form.postIds)
                    }
                  } catch (assocErr: any) {
                    console.warn('assign depts/posts failed', assocErr)
                    const assocMsg = assocErr && assocErr.response && (assocErr.response.data?.message || assocErr.response.data?.msg) ? (assocErr.response.data.message || assocErr.response.data.msg) : String(t('message.save_success') + '，但设置部门/岗位关联失败')
                    ElMessage.error(String(assocMsg))
                    router.push('/system/users')
                    return
                  }
                  router.push('/system/users')
                })
          } catch (e) { ElMessage.error(String(t('message.save_failed'))) } finally { saving.value = false }
        })
      }
    }

    function onCancel() { router.back() }
    onMounted(() => { load() })
    return { form, saving, formRef, rules, onSave, onCancel }
  }
})
</script>
