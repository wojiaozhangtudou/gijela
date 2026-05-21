<template>
  <div class="login-container">
    <div class="login-card">
      <!-- 语言切换 -->
      <div class="lang-selector">
        <el-select v-model="lang" size="small" @change="onLangChange">
          <el-option :label="$t('message.lang_zh')" value="zh" />
          <el-option :label="$t('message.lang_en')" value="en" />
        </el-select>
      </div>

      <!-- 头部 -->
      <div class="header">
        <h1 class="title">欢迎回来</h1>
        <p class="subtitle">请输入您的凭据以继续</p>
      </div>

      <!-- 登录表单 -->
      <el-form :model="form" ref="formRef" :rules="rules" class="login-form">
        <el-form-item prop="username" label="用户名或邮箱" :required="false">
          <el-input
            v-model="form.username"
            placeholder="用户名或邮箱"
            size="large"
            prefix-icon="Avatar"
            clearable
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-form-item prop="password" label="密码" :required="false">
          <el-input
            v-model="form.password"
            placeholder="密码"
            type="password"
            size="large"
            prefix-icon="Lock"
            show-password
            clearable
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <div class="form-options">
          <el-checkbox v-model="remember" class="remember-check">
            记住我
          </el-checkbox>
          <el-button type="text" class="forgot-link" @click="onForgot">
            忘记密码?
          </el-button>
        </div>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="onSubmit"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 分隔线 -->
      <div class="divider">
        <span>或</span>
      </div>

      <!-- 社交登录 -->
      <div class="social-login">
        <div class="social-btn" title="Google">
          <span class="social-icon">G</span>
        </div>
        <div class="social-btn" title="Facebook">
          <span class="social-icon">F</span>
        </div>
        <div class="social-btn" title="微信">
          <span class="social-icon">微</span>
        </div>
      </div>

      <!-- 底部链接 -->
      <div class="footer-links">
        <el-button type="text" class="register-link" @click="onRegister">
          注册新账户
        </el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

export default {
  name: 'Login',
  setup() {
    const router = useRouter()
    const { t, locale } = useI18n()
    const userStore = useUserStore()

    const form = ref({
      username: '',
      password: ''
    })

    const remember = ref(false)
    const loading = ref(false)
    const formRef = ref()

    const lang = computed({
      get: () => locale.value,
      set: (val) => {
        locale.value = val
        localStorage.setItem('lang', val)
      }
    })

    const rules = {
      username: [
        { required: true, message: t('message.username_required'), trigger: 'blur' }
      ],
      password: [
        { required: true, message: t('message.password_required'), trigger: 'blur' }
      ]
    }

    async function onSubmit() {
      if (!formRef.value) return

      try {
        await formRef.value.validate()
        loading.value = true

        const success = await userStore.login(form.value.username, form.value.password)

        if (success) {
          ElMessage.success(t('message.login_success') || '登录成功')

          if (remember.value) {
            localStorage.setItem('remember_username', form.value.username)
          } else {
            localStorage.removeItem('remember_username')
          }

          router.push('/dashboard')
        } else {
          ElMessage.error('登录失败，请检查用户名和密码')
        }
      } catch (error: any) {
        console.error('Login error:', error)
        ElMessage.error(error.message || '登录失败，请检查网络连接')
      } finally {
        loading.value = false
      }
    }

    function onLangChange(lang: string) {
      locale.value = lang
      localStorage.setItem('lang', lang)
    }

    function onForgot() {
      ElMessage.info(String(t('message.contact_admin') || '请联系管理员重置密码'))
    }

    function onRegister() {
      ElMessage.info('注册功能即将上线，请联系管理员')
    }

    onMounted(() => {
      const savedUsername = localStorage.getItem('remember_username')
      if (savedUsername) {
        form.value.username = savedUsername
        remember.value = true
      }
    })

    return {
      form,
      formRef,
      onSubmit,
      lang,
      onLangChange,
      rules,
      loading,
      remember,
      onForgot,
      onRegister
    }
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 360px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  position: relative;
}

.lang-selector {
  position: absolute;
  top: 20px;
  right: 20px;
}

.header {
  text-align: center;
  margin-bottom: 40px;
}

.title {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.subtitle {
  color: #666;
  font-size: 16px;
}

.login-form {
  margin-bottom: 30px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.login-form :deep(.el-form-item) {
  display: block !important;
  margin-bottom: 24px;
}

.login-form :deep(.el-form-item__content) {
  display: block !important;
  margin-left: 0 !important;
}

.login-form :deep(.el-form-item__label) {
  display: block !important;
  text-align: left;
  margin-bottom: 8px;
}

.login-form :deep(.el-input__inner) {
  border-radius: 8px;
  border: 1px solid #d1d5db;
  padding: 12px 16px;
  font-size: 16px;
  transition: all 0.3s ease;
  background-color: #ffffff;
}

.login-form :deep(.el-input__inner):focus {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
  outline: none;
}

.login-form :deep(.el-input__prefix) {
  color: #667eea;
  font-size: 18px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.remember-check :deep(.el-checkbox__label) {
  color: #666;
  font-size: 14px;
}

.forgot-link {
  color: #667eea !important;
  font-size: 14px;
  font-weight: 500;
}

.forgot-link:hover {
  color: #764ba2 !important;
}

.login-btn {
  width: 100%;
  height: 50px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.3);
}

/* 分隔线 */
.divider {
  text-align: center;
  margin: 30px 0;
  position: relative;
  color: #999;
  font-size: 14px;
}

.divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, #e1e5e9, transparent);
}

.divider span {
  background: rgba(255, 255, 255, 0.95);
  padding: 0 15px;
  position: relative;
}

/* 社交登录 */
.social-login {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-bottom: 30px;
}

.social-btn {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  background: #f5f5f5;
  border: 2px solid #e1e5e9;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 16px;
  font-weight: 600;
  color: #666;
}

.social-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #667eea;
  color: #667eea;
}

.social-icon {
  font-size: 18px;
  font-weight: bold;
}

/* 底部链接 */
.footer-links {
  text-align: center;
  margin-bottom: 20px;
}

.register-link {
  color: #667eea !important;
  font-size: 14px;
  font-weight: 500;
}

.register-link:hover {
  color: #764ba2 !important;
}

.footer {
  text-align: center;
  margin-top: 20px;
}

.footer p {
  color: #999;
  font-size: 14px;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .login-container {
    padding: 10px;
  }

  .login-card {
    padding: 30px 20px;
  }

  .title {
    font-size: 24px;
  }
}
</style>
