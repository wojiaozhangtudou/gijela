<template>
  <div class="profile-container">
    <!-- 顶部背景区域 -->
    <div class="profile-hero">
      <div class="hero-background"></div>
      <div class="hero-content">
        <div class="avatar-wrapper">
          <el-avatar :src="form.avatar" :size="70" class="hero-avatar">
            {{ form.nickname ? form.nickname[0] : '?' }}
          </el-avatar>
          <!-- hidden file input for avatar upload -->
          <input ref="fileRef" type="file" accept="image/*" style="display:none" @change="onFileChange" />
          <div class="avatar-badge" @click="onUploadAvatar">
            <el-icon size="14"><Camera /></el-icon>
          </div>
        </div>
        <div class="user-info">
          <h1 class="user-name">{{ form.nickname || form.username || $t('user.defaultName') }}</h1>
          <div class="user-meta">
            <span v-if="form.email" class="meta-item">
              <el-icon size="14"><Message /></el-icon>
              {{ form.email }}
            </span>
            <span v-if="form.phone" class="meta-item">
              <el-icon size="14"><Phone /></el-icon>
              {{ form.phone }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="profile-content">
      <!-- 基本信息卡片 -->
      <el-card class="info-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <div class="header-left">
              <el-icon class="header-icon" size="16"><User /></el-icon>
              <span class="header-title">{{ $t('user.profile') }}</span>
            </div>
            <el-tag type="success" size="small">{{ $t('message.status_active') }}</el-tag>
          </div>
        </template>

        <el-form :model="form" ref="formRef" :rules="rules" label-position="top" class="modern-form">
          <el-row :gutter="16">
            <el-col :lg="6" :md="12">
              <el-form-item :label="$t('message.username')" class="form-item compact">
                <el-input 
                  v-model="form.username" 
                  disabled 
                  size="small"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :lg="6" :md="12">
              <el-form-item :label="$t('message.nickname')" prop="nickname" class="form-item compact">
                <el-input 
                  v-model="form.nickname" 
                  :placeholder="$t('validation.enter_fullname')"
                  size="small"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :lg="6" :md="12">
              <el-form-item :label="$t('message.email')" prop="email" class="form-item compact">
                <el-input 
                  v-model="form.email" 
                  :placeholder="$t('message.enter_email')"
                  size="small"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :lg="6" :md="12">
              <el-form-item :label="$t('profile.phone')" prop="phone" class="form-item compact">
                <el-input 
                  v-model="form.phone" 
                  :placeholder="$t('message.enter_phone')"
                  size="small"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 操作按钮区域 -->
          <div class="action-section compact">
            <el-space size="small" wrap>
              <el-button 
                size="small" 
                @click="onCancel"
                class="action-btn secondary-btn"
              >
                <el-icon size="14"><RefreshLeft /></el-icon>
                {{ $t('message.cancel') }}
              </el-button>
              <el-button 
                type="primary" 
                size="small"
                :loading="saving" 
                @click="onSave"
                class="action-btn primary-btn"
              >
                <el-icon size="14"><Check /></el-icon>
                {{ $t('message.save') }}
              </el-button>
              <el-button 
                type="warning" 
                size="small"
                @click="onChangePassword"
                class="action-btn warning-btn"
                plain
              >
                <el-icon size="14"><Lock /></el-icon>
                {{ $t('profile.change_password') }}
              </el-button>
            </el-space>
          </div>
        </el-form>
      </el-card>

      <!-- 安全设置卡片 -->
      <el-card class="security-card" shadow="hover">
        <template #header>
          <div class="card-header compact">
            <div class="header-left">
              <el-icon class="header-icon" size="16"><Lock /></el-icon>
              <span class="header-title">安全设置</span>
            </div>
          </div>
        </template>
        <div class="security-content compact">
          <div class="security-item compact">
            <div class="security-info">
              <h4>{{ $t('profile.change_password') }}</h4>
              <p>定期更换密码可以提高账户安全性</p>
            </div>
            <el-button type="primary" plain size="small" @click="onChangePassword">
              修改密码
            </el-button>
          </div>
          <div class="security-item compact">
            <div class="security-info">
              <h4>登录设备管理</h4>
              <p>查看和管理您的登录设备，及时发现异常登录</p>
            </div>
            <el-button type="info" plain size="small" @click="onManageDevices">
              管理设备
            </el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref, onMounted } from 'vue'
import { getCurrentUser, saveUser, updateAvatar, getUserAvatar } from '../api/user'
import { ElMessage } from 'element-plus'
import { showApiResult } from '../utils/apiResult'
import { useI18n } from 'vue-i18n'
import { User, Upload, Check, RefreshLeft, Lock, Camera, Message, Phone, EditPen } from '@element-plus/icons-vue'

export default defineComponent({
  components: {
    User,
    Upload,
    Check,
    RefreshLeft,
    Lock,
    Camera,
    Message,
    Phone,
    EditPen
  },
  setup() {
    const { t } = useI18n()
  const AVATAR_MAX_BASE64_LEN = 60000
  const formRef = ref()
  const fileRef = ref<HTMLInputElement | null>(null)
  const saving = ref(false)
  const uploading = ref(false)
    const form = reactive<any>({ id: null, username: '', nickname: '', email: '', phone: '', avatar: '', status: '1' })

    const rules = {
      nickname: [{ required: false, message: String(t('validation.enter_fullname')), trigger: 'blur' }],
      email: [{ type: 'email', message: String(t('validation.invalid_email')), trigger: 'blur' }],
  // allow 10 or 11 digits (some test numbers may be 10 digits like '1111111113')
  phone: [{ pattern: /^\d{10,11}$/, message: String(t('validation.invalid_phone')), trigger: 'blur' }]
    }

    async function load() {
      try {
        const r = await getCurrentUser()
        // r may be ApiResponse or raw user object. Normalize to a user object.
        let data: any = r
        if (r && typeof r === 'object') {
          if ('data' in r) data = r.data
        }
        // handle nested shapes: { data: { user: {...} } } or { data: { data: {...} } }
        if (data && typeof data === 'object') {
          if (data.user) data = data.user
          else if (data.data) data = data.data
        }

        if (!data) return

        // Map common backend fields into our form shape
        const mapped: any = {}
        mapped.id = data.id ?? data.userId ?? form.id
        mapped.username = data.username ?? data.loginName ?? data.account ?? form.username
        mapped.nickname = data.nickname ?? data.fullname ?? data.name ?? form.nickname
        mapped.email = data.email ?? data.mail ?? form.email
        mapped.phone = data.phone ?? data.mobile ?? data.tel ?? form.phone
        mapped.avatar = data.avatar ?? data.avatarUrl ?? data.avatar_url ?? form.avatar
        mapped.status = data.status ?? form.status

  Object.assign(form, mapped)
  // load avatar after mapping id
  await fetchAvatar()
      } catch (e) {
        // fail silently; caller already shows messages on save
        console.debug('load current user failed', e)
      }
    }

    async function onSave() {
      if (!formRef.value) return
      ;(formRef.value as any).validate(async (valid: boolean) => {
        if (!valid) return
        saving.value = true
        try {
          const payload = { ...form }
          const res: any = await saveUser(payload)
          showApiResult(res, String(t('message.save_success')))
        } catch (e) {
          ElMessage.error(String(t('message.save_failed')))
        } finally {
          saving.value = false
        }
      })
    }

    function onCancel() { load() }
    function onUploadAvatar() {
      // trigger hidden file input
      if (fileRef.value) fileRef.value.click()
    }

    async function onFileChange(e: Event) {
      const input = e.target as HTMLInputElement
      if (!input || !input.files || input.files.length === 0) return
      const file = input.files[0]
      try {
        uploading.value = true
        const base64 = await toAvatarDataUrl(file)
        const cleaned = base64.replace(/^data:.*;base64,/, '')
        if (cleaned.length > AVATAR_MAX_BASE64_LEN) {
          ElMessage.error('头像文件过大，请选择更小的图片后重试')
          return
        }
        const payload = { userId: form.id, avatarBase64: cleaned }
        const res: any = await updateAvatar(payload)
        showApiResult(res, String(t('message.save_success')), () => {
          form.avatar = base64
        })
      } catch (err) {
        ElMessage.error(String(t('message.save_failed')))
      } finally {
        uploading.value = false
        // clear input
        input.value = ''
      }
    }

    function readFileAsDataUrl(file: File): Promise<string> {
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve((reader.result as string) || '')
        reader.onerror = () => reject(new Error('读取文件失败'))
        reader.readAsDataURL(file)
      })
    }

    function loadImage(dataUrl: string): Promise<HTMLImageElement> {
      return new Promise((resolve, reject) => {
        const img = new Image()
        img.onload = () => resolve(img)
        img.onerror = () => reject(new Error('图片解析失败'))
        img.src = dataUrl
      })
    }

    async function toAvatarDataUrl(file: File): Promise<string> {
      const originDataUrl = await readFileAsDataUrl(file)
      const cleanedOrigin = originDataUrl.replace(/^data:.*;base64,/, '')
      if (cleanedOrigin.length <= AVATAR_MAX_BASE64_LEN) return originDataUrl

      const img = await loadImage(originDataUrl)
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')
      if (!ctx) return originDataUrl

      const sideCandidates = [256, 192, 160, 128, 96]
      const qualityCandidates = [0.82, 0.72, 0.62, 0.52, 0.42, 0.32]
      let lastDataUrl = originDataUrl

      for (const side of sideCandidates) {
        const scale = Math.min(1, side / Math.max(img.width, img.height))
        const width = Math.max(1, Math.round(img.width * scale))
        const height = Math.max(1, Math.round(img.height * scale))
        canvas.width = width
        canvas.height = height
        ctx.clearRect(0, 0, width, height)
        ctx.drawImage(img, 0, 0, width, height)

        for (const quality of qualityCandidates) {
          const dataUrl = canvas.toDataURL('image/jpeg', quality)
          lastDataUrl = dataUrl
          const cleaned = dataUrl.replace(/^data:.*;base64,/, '')
          if (cleaned.length <= AVATAR_MAX_BASE64_LEN) {
            return dataUrl
          }
        }
      }

      return lastDataUrl
    }

    async function fetchAvatar() {
      if (!form.id) return
      try {
        const res: any = await getUserAvatar(form.id)
        if (res && res.code === 0 && res.data) {
          const d = res.data
          if (typeof d === 'string') {
            form.avatar = d.startsWith('data:') ? d : 'data:image/png;base64,' + d
          } else if (typeof d === 'object') {
            const b = d.base64 || d.avatarBase64 || d.data || null
            const mime = d.mime || d.contentType || d.type || 'image/png'
            if (b && typeof b === 'string') {
              form.avatar = b.startsWith('data:') ? b : `data:${mime};base64,` + b
            }
          }
        }
      } catch (e) {
        // ignore
      }
    }

    function onChangePassword() { ElMessage.info(String(t('profile.change_password')) + ' - 功能待实现') }
    function onManageDevices() { ElMessage.info('登录设备管理 - 功能待实现') }

  onMounted(() => { load() })
  return { form, formRef, fileRef, rules, saving, uploading, onSave, onCancel, onUploadAvatar, onFileChange, onChangePassword, onManageDevices }
  }
})
</script>

<style scoped>
.profile-container {
  min-height: auto;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 0;
  overflow: hidden;
}

/* 顶部英雄区域 */
.profile-hero {
  position: relative;
  height: 180px;
  overflow: hidden;
  margin-bottom: -30px;
}

.hero-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  opacity: 0.9;
}

.hero-background::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><pattern id="grain" width="100" height="100" patternUnits="userSpaceOnUse"><circle cx="20" cy="20" r="1" fill="white" opacity="0.1"/><circle cx="80" cy="80" r="1" fill="white" opacity="0.1"/><circle cx="40" cy="60" r="1" fill="white" opacity="0.1"/></pattern></defs><rect width="100" height="100" fill="url(%23grain)"/></svg>');
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 12px;
  color: white;
  text-align: center;
}

.avatar-wrapper {
  position: relative;
  margin-bottom: 8px;
}

.hero-avatar {
  border: 2px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  transition: all 0.3s ease;
  width: 70px;
  height: 70px;
}

.hero-avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
}

.avatar-badge {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 24px;
  height: 24px;
  background: #409eff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 2px solid white;
  transition: all 0.3s ease;
  font-size: 12px;
}

.avatar-badge:hover {
  background: #337ecc;
  transform: scale(1.1);
}

.user-info {
  text-align: left;
  margin-left: 20px;
}

.user-name {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 4px 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.user-username {
  font-size: 12px;
  opacity: 0.8;
  margin: 0 0 8px 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  opacity: 0.9;
}

/* 主要内容区域 */
.profile-content {
  max-width: 800px;
  margin: 40px auto 0;
  padding: 0 16px 16px;
  position: relative;
  z-index: 3;
}

/* 卡片样式 */
.info-card, .security-card {
  border-radius: 8px;
  border: none;
  margin-bottom: 12px;
  overflow: hidden;
  backdrop-filter: blur(10px);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.info-card:hover, .security-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 20px;
  color: #409eff;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

/* 表单样式 */
.modern-form {
  padding: 12px 0;
}

.form-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin: 0 0 12px 0;
  padding-bottom: 4px;
  border-bottom: 2px solid #f0f2f6;
}

.form-item {
  margin-bottom: 12px;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  color: #606266;
  font-size: 16px;
  padding-bottom: 8px;
}

:deep(.el-input) {
  border-radius: 12px;
}

:deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  font-size: 16px;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

:deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.readonly-input :deep(.el-input__wrapper) {
  background-color: #f8f9fa;
  cursor: not-allowed;
}

/* 操作按钮区域 */
.action-section {
  margin-top: 16px;
  padding: 16px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border-radius: 8px;
  text-align: center;
}

.action-btn {
  border-radius: 6px;
  padding: 8px 20px;
  font-weight: 600;
  font-size: 16px;
  transition: all 0.3s ease;
  border: none;
  position: relative;
  overflow: hidden;
}

.action-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s;
}

.action-btn:hover::before {
  left: 100%;
}

.primary-btn {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.primary-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.4);
}

.secondary-btn {
  background: linear-gradient(135deg, #909399 0%, #73767a 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(144, 147, 153, 0.3);
}

.warning-btn {
  border: 2px solid #e6a23c;
  color: #e6a23c;
  background: transparent;
}

.warning-btn:hover {
  background: #e6a23c;
  color: white;
  transform: translateY(-2px);
}

/* 安全设置卡片 */
.security-content {
  padding: 12px 0;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.security-info h4 {
  margin: 0 0 4px 0;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.security-info p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.security-item :deep(.el-button) {
  font-size: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-hero {
    height: 160px;
    margin-bottom: -20px;
  }
  
  .hero-content {
    padding: 10px;
  }
  
  .user-info {
    margin-left: 15px;
  }
  
  .user-name {
    font-size: 20px;
  }
  
  .profile-content {
    padding: 0 12px 12px;
  }
  
  .action-section {
    padding: 12px;
  }
  
  .action-btn {
    width: 100%;
    margin-bottom: 6px;
  }
  
  .security-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding: 10px 0;
  }
}

/* 动画效果 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.info-card, .security-card {
  animation: fadeInUp 0.6s ease-out;
}

.info-card {
  animation-delay: 0.1s;
}

.security-card {
  animation-delay: 0.2s;
}
</style>
