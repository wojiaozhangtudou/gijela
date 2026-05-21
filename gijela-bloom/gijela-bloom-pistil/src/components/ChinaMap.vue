<template>
  <div class="china-map-container">
    <div class="china-map-placeholder">
      <h3>中国地图</h3>
      <p>地图功能正在开发中...</p>
      <div class="province-grid">
        <div
          v-for="province in provinces"
          :key="province"
          class="province-item"
          @click="handleProvinceClick(province)"
        >
          {{ province }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const provinces = [
  '北京市', '天津市', '上海市', '重庆市',
  '河北省', '山西省', '辽宁省', '吉林省',
  '黑龙江省', '江苏省', '浙江省', '安徽省',
  '福建省', '江西省', '山东省', '河南省',
  '湖北省', '湖南省', '广东省', '广西壮族自治区',
  '海南省', '四川省', '贵州省', '云南省',
  '西藏自治区', '陕西省', '甘肃省', '青海省',
  '宁夏回族自治区', '新疆维吾尔自治区', '台湾省',
  '香港特别行政区', '澳门特别行政区'
]

const handleProvinceClick = (provinceName: string) => {
  // 跳转到菜单页面，传递省份参数
  router.push({
    path: '/menu',
    query: {
      province: provinceName,
      type: 'province'
    }
  })
}
</script>

<style scoped>
.china-map-container {
  width: 100%;
  padding: 20px;
}

.china-map-placeholder {
  background: white;
  border-radius: 8px;
  padding: 30px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.china-map-placeholder h3 {
  color: #333;
  margin-bottom: 10px;
  font-size: 24px;
}

.china-map-placeholder p {
  color: #666;
  margin-bottom: 30px;
  font-size: 16px;
}

.province-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 15px;
  max-width: 1000px;
  margin: 0 auto;
}

.province-item {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.province-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}
</style>
