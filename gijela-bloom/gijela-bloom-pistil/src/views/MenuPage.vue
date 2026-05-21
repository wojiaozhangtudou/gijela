<template>
  <div class="menu-page">
    <div class="page-header">
      <h3>{{ title }}</h3>
      <div v-if="province" class="province-info">
        <el-tag type="primary" size="large">{{ province }}</el-tag>
        <p>您选择了 {{ province }}，这里可以显示该省份的相关信息</p>
      </div>
    </div>
    <p>{{ $t('message.menu_placeholder', { id }) }}</p>

    <div v-if="province" class="province-actions">
      <el-button type="primary" @click="viewCities">查看城市</el-button>
      <el-button type="success" @click="viewDistricts">查看区县</el-button>
      <el-button @click="backToMap">返回地图</el-button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

export default defineComponent({
  setup() {
    const route = useRoute()
    const router = useRouter()
    const { t } = useI18n()

    const title = computed(() => {
      const metaTitle = (route.meta && route.meta.title) ? String(route.meta.title) : ''
      if (metaTitle) {
        try { return String(t(metaTitle)) } catch (e) { return metaTitle }
      }
      if (typeof route.name === 'string' && route.name) {
        try { return String(t(String(route.name))) } catch (e) { return String(route.name) }
      }
      return String(t('message.page'))
    })

    const id = route.params.id || ''
    const province = computed(() => route.query.province as string)
    const type = computed(() => route.query.type as string)

    const viewCities = () => {
      ElMessage.info('城市查看功能开发中...')
    }

    const viewDistricts = () => {
      ElMessage.info('区县查看功能开发中...')
    }

    const backToMap = () => {
      router.push('/dashboard')
    }

    return {
      title,
      id,
      province,
      type,
      viewCities,
      viewDistricts,
      backToMap
    }
  }
})
</script>

<style scoped>
.menu-page {
  padding: 24px;
}

.page-header {
  margin-bottom: 20px;
}

.province-info {
  margin-top: 16px;
}

.province-info p {
  margin: 8px 0 0 0;
  color: #666;
}

.province-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

.province-actions .el-button {
  min-width: 100px;
}
</style>
