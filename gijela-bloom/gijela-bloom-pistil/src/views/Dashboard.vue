<template>
  <div class="dashboard-container">
    <ExternalAppsPanel v-if="externalLinks.length" :items="externalLinks" />
    <!-- 中国地图暂时隐藏 -->
    <!-- <div class="dashboard-content">
      <ChinaMap />
    </div> -->
  </div>
</template>

<script setup lang="ts">
// @ts-ignore script setup default export
import ChinaMap from '@/components/ChinaMap.vue'
// @ts-ignore script setup default export
import ExternalAppsPanel from '@/components/ExternalAppsPanel.vue'
import { useUserStore } from '@/store/user'
import { computed } from 'vue'
const userStore = useUserStore()
const externalLinks = computed(() => userStore.externalLinks || [])
</script>

<style scoped>
.dashboard-container { padding: 24px; }

.dashboard-header {
  margin-bottom: 24px;
  text-align: center;
}

.dashboard-header h2 {
  color: #333;
  font-size: 28px;
  margin-bottom: 8px;
}

.dashboard-header p {
  color: #666;
  font-size: 16px;
}

.dashboard-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}
</style>
