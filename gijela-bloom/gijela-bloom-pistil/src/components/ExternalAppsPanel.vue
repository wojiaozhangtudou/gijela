<template>
  <div class="external-apps-panel">
    <div class="panel-header">
      <h3 class="panel-title">外部系统 / External Apps</h3>
      <div class="panel-sub" v-if="items && items.length">共 {{ items.length }} 个入口</div>
    </div>
    <div v-if="!items || !items.length" class="empty-hint">暂无外部系统入口</div>
    <div v-else class="apps-grid">
      <div
        v-for="app in items"
        :key="app.id"
        class="app-card"
        :title="fullTooltip(app)"
        @click="onOpen(app)"
      >
        <div class="icon-wrap">
          <el-icon v-if="app.icon" :size="26">
            <component :is="app.icon" />
          </el-icon>
          <div v-else class="icon-fallback">↗</div>
        </div>
        <div class="info">
          <div class="name" :class="{ ellipsis: true }">{{ resolveName(app) }}</div>
          <div class="path" v-if="app.path">{{ shortPath(app.path) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { openExternal } from '@/utils/openExternal'
import { useI18n } from 'vue-i18n'

interface Item { id: string | number; name?: string; path?: string; icon?: string; status?: any }

const props = defineProps<{ items: Item[] }>()
const { t } = useI18n()

function resolveName(app: Item) {
  const n = app.name || ''
  try {
    const direct = t(n)
    if (direct && direct !== n) return direct
    const pref = t('message.' + n)
    if (pref && pref !== 'message.' + n) return pref
  } catch (e) {}
  return n
}

function shortPath(p?: string) {
  if (!p) return ''
  if (/^https?:\/\//i.test(p)) {
    try {
      const u = new URL(p)
      return u.host
    } catch { return p }
  }
  return p
}

function fullTooltip(app: Item) {
  return `${resolveName(app)}\n${app.path || ''}`
}

function onOpen(app: Item) {
  openExternal(app as any)
}
</script>

<style scoped>
.external-apps-panel { margin-top: 24px; background:#fff; border:1px solid #eee; border-radius:8px; padding:16px 20px; }
.panel-header { display:flex; align-items:baseline; gap:12px; margin-bottom:12px; }
.panel-title { font-size:16px; font-weight:600; margin:0; }
.panel-sub { font-size:12px; color:#888; }
.empty-hint { font-size:13px; color:#999; padding:16px 4px; }
.apps-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(180px,1fr)); gap:14px; }
.app-card { cursor:pointer; display:flex; gap:12px; align-items:center; padding:12px 14px; background:#fafbfc; border:1px solid #e5e7eb; border-radius:8px; transition:.18s; position:relative; min-height:70px; }
.app-card:hover { background:#f0f7ff; border-color:#409eff; box-shadow:0 2px 8px rgba(64,158,255,.15); }
.icon-wrap { width:40px; height:40px; border-radius:8px; background:#fff; display:flex; align-items:center; justify-content:center; box-shadow:0 1px 3px rgba(0,0,0,.08); }
.icon-fallback { font-size:18px; color:#409eff; font-weight:600; }
.info { flex:1; display:flex; flex-direction:column; }
.name { font-weight:500; font-size:14px; line-height:1.2; }
.path { font-size:12px; color:#666; margin-top:4px; word-break:break-all; }
.ellipsis { overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
</style>
