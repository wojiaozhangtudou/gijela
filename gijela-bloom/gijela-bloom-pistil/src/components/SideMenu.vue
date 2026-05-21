<template>
  <el-menu :default-active="$route.path" router class="el-menu-vertical-demo app-side-menu" @select="onSelect">
    <template v-for="m in menus" :key="m.id">
      <component :is="(m.children && m.children.length) ? 'el-sub-menu' : 'el-menu-item'" :index="(m.path ? (m.path.startsWith('/') ? m.path : '/' + m.path) : ('/m/'+m.id))">
        <template v-if="m.children && m.children.length" #title>
          <div style="display: flex; align-items: center; gap: 8px;">
            <el-icon v-if="m.icon" :size="16">
              <component :is="m.icon" />
            </el-icon>
            <span>{{ getLabel(m) }}</span>
          </div>
        </template>
        <template v-if="m.children && m.children.length">
          <el-menu-item v-for="c in m.children" :index="(c.path ? (c.path.startsWith('/') ? c.path : '/' + c.path) : ('/m/'+c.id))" :key="c.id">
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-icon v-if="c.icon" :size="16">
                <component :is="c.icon" />
              </el-icon>
              <span>{{ getLabel(c) }}</span>
            </div>
          </el-menu-item>
        </template>
        <template v-else>
          <div style="display: flex; align-items: center; gap: 8px;">
            <el-icon v-if="m.icon" :size="16">
              <component :is="m.icon" />
            </el-icon>
            <span>{{ getLabel(m) }}</span>
          </div>
        </template>
      </component>
    </template>
  </el-menu>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { useTabsStore } from '../store/tabs'
import { 
  House, User, Setting, Menu, Document, Folder, List, Grid, 
  Plus, Edit, Delete, Search, Star, Share, Download, 
  Upload, Phone, Calendar, Clock 
} from '@element-plus/icons-vue'

export default defineComponent({
  components: { 
    RouterLink,
    House, User, Setting, Menu, Document, Folder, List, Grid, 
    Plus, Edit, Delete, Search, Star, Share, Download, 
    Upload, Phone, Calendar, Clock
  },
  props: {
    menus: { type: Array as () => any[], default: () => [] }
  },
  setup(props) {
  const { t } = (useI18n as any) ? (useI18n as any)() : { t: (k: string) => k }
  const tabsStore = useTabsStore()
    const normalize = (p: any) => {
      if (!p) return ''
      return typeof p === 'string' ? (p.startsWith('/') ? p : '/' + p) : String(p)
    }
    function getLabel(item: any) {
      if (!item) return ''
      const name = item.name || item.title || ''
      try {
        // try direct translation using vue-i18n
        const direct = t(name)
        if (direct && direct !== name) return direct
        // try with message. prefix (some backends send bare keys)
        const alt = t('message.' + name)
        if (alt && alt !== ('message.' + name)) return alt
      } catch (e) {}
      return name
    }

  function findItemByIndex(index: string, list: any[]): any | null {
      // index here is expected to be a path like '/something' or '/m/<id>'
      for (const it of list || []) {
        const path = it.path ? (it.path.startsWith('/') ? it.path : '/' + it.path) : ('/m/' + it.id)
        if (path === index) return it
        if (it.children && it.children.length) {
          const found = findItemByIndex(index, it.children)
          if (found) return found
        }
      }
      return null
    }

    function onSelect(index: string): void {
      // resolve the menu item from the props.menus passed into this component
      const item = findItemByIndex(index, Array.isArray(props.menus) ? props.menus : [])
      const title = item ? getLabel(item) : index
      tabsStore.open(index, title)
    }
  return { normalize, getLabel, onSelect }
  }
})
</script>
