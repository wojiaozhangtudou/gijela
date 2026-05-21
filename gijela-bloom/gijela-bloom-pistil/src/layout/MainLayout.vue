<template>
  <div class="main-layout" style="display:flex;height:100vh">
  <aside style="width:220px;border-right:1px solid #eee;flex:0 0 220px;display:flex;flex-direction:column">
      <SideMenu :menus="menus" @navigate="onNavigate" />
    </aside>
    <div style="flex:1;display:flex;flex-direction:column">
      <header class="app-header">
        <div class="header-left">
          <!-- 应用品牌区域 -->
          <div class="header-brand">
            <div class="brand-text">
              <span class="brand-name">智能管理平台</span>
            </div>
          </div>
          <!-- breadcrumb moved here (left-top) -->
          <div class="header-breadcrumb" v-if="breadcrumbs && breadcrumbs.length">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item v-for="(b, idx) in breadcrumbs" :key="idx">
                <a @click.prevent="goBreadcrumb(b.path)" style="cursor:pointer;color:var(--el-color-primary)">{{ b.title }}</a>
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>
  <!-- header-center removed per request -->
        <div class="header-right">
          <!-- breadcrumb moved to header-left -->
          <el-dropdown>
            <span class="el-dropdown-link" style="display:flex;align-items:center;gap:10px">
              <div class="user-info">
                <el-tooltip v-if="profile" :content="profile?.nickname || profile?.realName || profile?.username || $t('user.defaultName')" placement="bottom">
                  <div class="user-name">
                    <span class="marquee" :class="{ 'is-long': isLong(profile?.nickname || profile?.realName || profile?.username) }">{{ profile?.nickname || profile?.realName || profile?.username || $t('user.defaultName') }}</span>
                  </div>
                </el-tooltip>
                <el-tooltip v-if="profile?.email" :content="profile.email" placement="bottom">
                  <div class="user-sub">
                    <span class="marquee" :class="{ 'is-long': isLong(profile.email) }">{{ shortEmail(profile.email) }}</span>
                  </div>
                </el-tooltip>
              </div>
              <el-avatar size="36" :src="profile?.avatar" />
              <i class="el-icon-arrow-down" style="font-size:14px;color:#666;margin-left:8px" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goProfile">{{ $t('user.profile') }}</el-dropdown-item>
                <el-dropdown-item divided @click="logout">{{ $t('user.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
        <!-- Tab bar -->
        <div class="tabs-bar">
          <div class="tabs-wrapper">
            <div class="tabs-scroll" ref="tabsScroll">
              <el-tabs v-model="activeTab" type="card" @tab-click="onTabClick" @tab-remove="onTabRemove" class="tabs-el">
                <el-tab-pane v-for="tab in tabs" :key="tab.path" :label="tab.title" :name="tab.path" :closable="tab.closable"></el-tab-pane>
              </el-tabs>
            </div>
            <div class="tabs-fade left" aria-hidden="true"></div>
            <div class="tabs-fade right" aria-hidden="true"></div>
          </div>
          <div class="tabs-actions">
            <el-dropdown>
              <template #default>
                <el-button :title="actionsLabel" type="primary" plain size="small" class="tabs-actions-btn">
                  <i class="el-icon-more" aria-hidden="true" />
                  <span class="tabs-actions-text">{{ actionsLabel }}</span>
                </el-button>
              </template>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="closeAllTabs">{{ closeAllLabel }}</el-dropdown-item>
                  <el-dropdown-item @click="closeOtherTabs">{{ closeOthersLabel }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
  <main style="flex:1;padding:16px">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import SideMenu from '../components/SideMenu.vue'
import { useUserStore } from '../store/user'
import { useTabsStore } from '../store/tabs'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'

export default defineComponent({
  components: { SideMenu },
  setup() {
    const store = useUserStore()
    const router = useRouter()
    const profile = computed(() => store.profile)
    const menus = computed(() => store.menus)
    const route = useRoute()
    const { locale } = useI18n()
        const { t } = useI18n()

        // helper: safe translate with fallback (vue-i18n may return key when missing)
        function safeT(key: string, fallback: string) {
          try {
            const v = String(t(key))
            // 检查翻译是否为空或等于原key
            if (!v || v === key) {
              // 如果key包含点号，尝试按层级单独翻译
              if (key.includes('.')) {
                const parts = key.split('.')
                const lastPart = parts[parts.length - 1]
                const parentKey = parts.slice(0, -1).join('.')
                
                // 尝试翻译父级
                const parent = String(t(parentKey))
                
                // 尝试翻译最后一部分
                const lastPartTranslated = String(t(lastPart))
                
                // 如果最后一部分有翻译且不等于自身，使用它
                if (lastPartTranslated && lastPartTranslated !== lastPart) {
                  return lastPartTranslated
                }
              }
              return fallback
            }
            return v
          } catch (e) {
            return fallback
          }
        }

        const actionsLabel = computed(() => safeT('message.actions', '操作'))
        const closeAllLabel = computed(() => safeT('message.close_all', '关闭所有'))
        const closeOthersLabel = computed(() => safeT('message.close_others', '只保留当前'))
      const currentTitle = computed(() => {
        // 特殊处理个人资料页面
        if (route.path === '/profile' || route.name === 'Profile') {
          return locale.value === 'zh' ? '个人资料' : 'Profile'
        }
        
        // Prefer explicit route meta.title translation first
        const metaTitle = route.meta && route.meta.title ? String(route.meta.title) : ''
        if (metaTitle) {
          try {
            const translated = String(t(metaTitle))
            // 如果翻译结果不是空且不等于原key，则使用翻译结果
            if (translated && translated !== metaTitle) {
              return translated
            }
            // 如果翻译等于key，使用safeT处理，确保正确回退
            return safeT(metaTitle, metaTitle)
          } catch (e) {
            // 捕获异常，使用safeT处理
            return safeT(metaTitle, metaTitle)
          }
        }

        // Then try menu tree labels (translated)
        try {
          const menusTree = menus?.value || []
          const match = (function find(nodes: any[]): any {
            if (!nodes || !nodes.length) return null
            for (const n of nodes) {
              // match by name or path (normalize)
              if (n.name && String(n.name) === String(route.name)) return n
              if (n.path) {
                const np = String(n.path).replace(/\/+$/, '')
                const rp = String(route.path || '').replace(/\/+$/, '')
                if (np === rp) return n
              }
              if (n.children) {
                const f = find(n.children)
                if (f) return f
              }
            }
            return null
          })(menusTree)
          if (match) {
            // try translate menu.name first, then menu.title, then raw name
            if (match.name) {
              // 使用safeT函数来翻译menu.name
              return safeT(String(match.name), String(match.name))
            }
            if (match.title) {
              // 使用safeT函数来翻译menu.title
              return safeT(String(match.title), String(match.title))
            }
          }
        } catch (e) {}

        // fallback to route.name then path
        if (typeof route.name === 'string') return route.name
        return route.path || ''
      })

    const isDashboard = computed(() => {
      return route.path === '/dashboard' || route.name === 'dashboard'
    })

    // try to build breadcrumb titles from route.meta.title, fallback to menus tree titles
    const breadcrumbs = computed(() => {
      // Build accumulated paths from route.matched and replace dynamic params
      const items: Array<{ title: string; path: string }> = []
      if (!route.matched || !route.matched.length) return items
      // accumulate segments
      const segs: string[] = []
      // helper: find menu node title by path or name
      function findMenuTitle(nodes: any[], matcher: (node: any) => boolean): string | null {
        if (!nodes || !nodes.length) return null
        for (const n of nodes) {
          if (matcher(n)) {
            // Prefer menu.name (often the display name). Try translating if it's a key, otherwise return raw.
            if (n.name) {
              // 使用safeT函数来翻译menu.name
              return safeT(String(n.name), String(n.name))
            }
            // fallback to title if name is not present
            if (n.title) {
              // 使用safeT函数来翻译menu.title
              return safeT(String(n.title), String(n.title))
            }
          }
          if (n.children) {
            const found = findMenuTitle(n.children, matcher)
            if (found) return found
          }
        }
        return null
      }

      route.matched.forEach((r) => {
        const raw = r.path || ''
        if (raw) segs.push(raw)
        const acc = ('/' + segs.join('/')).replace(/\/+/g, '/')

        // Prefer menu tree label first (menu.name / menu.title) so breadcrumbs match the menu
        let title: string | null = null
        try {
          const menusTree = menus?.value || []
          if (typeof r.name === 'string' && r.name) {
            title = findMenuTitle(menusTree, (n: any) => String(n.name) === String(r.name))
          }
          if (!title) {
            title = findMenuTitle(menusTree, (n: any) => {
              if (!n.path) return false
              const np = String(n.path).replace(/\/+$/, '')
              const ap = String(acc).replace(/\/+$/, '')
              return np === ap
            })
          }
        } catch (e) {}

        // 优先使用 meta.title 再回退到 route.name，确保像 user.profile 这种 key 被正确翻译
        if (!title && r.meta && r.meta.title) {
          const titleKey = String(r.meta.title)
          title = safeT(titleKey, titleKey)
        }

        // 然后再尝试 route.name（多数情况下是英文标识，不一定需要翻译）
        if (!title && typeof r.name === 'string' && r.name) {
          title = safeT(String(r.name), String(r.name))
        }

        if (!title) {
          try {
            const allRoutes = router.getRoutes()
            const foundByName = r.name ? allRoutes.find(rt => rt.name === r.name) : null
            if (foundByName && foundByName.meta && foundByName.meta.title) {
              try { title = String(t(String(foundByName.meta.title))) } catch (e) { title = String(foundByName.meta.title) }
            }
            if (!title) {
              const foundByPath = allRoutes.find(rt => {
                if (!rt.path) return false
                const np = String(rt.path).replace(/\/+$/, '')
                const ap = String(acc).replace(/\/+$/, '')
                return np === ap
              })
              if (foundByPath && foundByPath.meta && foundByPath.meta.title) {
                try { title = String(t(String(foundByPath.meta.title))) } catch (e) { title = String(foundByPath.meta.title) }
              }
            }
          } catch (e) {}
        }

        // heuristic fallback
        if (!title) {
          const p = String(acc).toLowerCase()
          if (p.includes('users')) title = String(t('message.user_management') || '用户管理')
          else if (p.includes('roles')) title = String(t('message.role_management') || '角色管理')
        }

        if (title) {
          let realPath = acc
          Object.entries(route.params || {}).forEach(([k, v]) => {
            realPath = realPath.replace(new RegExp(':' + k, 'g'), String(v))
          })
          items.push({ title, path: realPath })
        }
      })
      return items
    })

    function goBreadcrumb(path: string) {
      if (!path) return
      router.push(path)
    }

    function onNavigate(path: string) {
      router.push(path)
    }

    function goProfile() {
      router.push('/profile')
    }

    function logout() {
      store.logout()
      router.push('/login')
    }

    // tabs store via Pinia
    const tabsStore = useTabsStore()
    const tabs = computed(() => tabsStore.tabs)
    const activeTab = computed({ get: () => tabsStore.active, set: (v: string) => { tabsStore.active = v } })

    function shortEmail(email?: string) {
      if (!email) return ''
      const parts = String(email).split('@')
      if (parts.length !== 2) return email
      const [local, domain] = parts
      // keep local part, shorten domain's middle if too long
      if (domain.length <= 18) return `${local}@${domain}`
      const head = domain.slice(0, 6)
      const tail = domain.slice(-6)
      return `${local}@${head}...${tail}`
    }

    function isLong(text?: string) {
      if (!text) return false
      // count characters; treat chinese chars as 2 for safety, but simplest: length > 5
      // we want fixed 5 汉字 width; if length > 5 mark as long
      return String(text).length > 5
    }

    // init tabs store on mounted
    onMounted(() => {
      tabsStore.init([{ title: safeT('message.dashboard', '首页'), path: '/dashboard', closable: false }])
      // ensure current route is opened as a tab
      if (route && route.path) {
          // 优先 meta.title -> currentTitle -> route.name -> path
          let title = ''
          if (route.meta?.title) title = safeT(String(route.meta.title), String(route.meta.title))
          if (!title) title = currentTitle.value
          if (!title && typeof route.name === 'string') title = safeT(String(route.name), String(route.name))
          if (!title) title = String(route.path)
          tabsStore.open(route.path, title)
      }
      // Debug: print initial tabs to browser console for inspection
      try { console.info('[debug] tabs initial:', JSON.parse(JSON.stringify(tabsStore.tabs))) } catch (e) { console.info('[debug] tabs initial (raw):', tabsStore.tabs) }
    })

    // Debug: watch tabs changes and print to console for easy debugging
    watch(() => tabs.value, (newVal) => {
      try { console.info('[debug] tabs changed:', JSON.parse(JSON.stringify(newVal))) } catch (e) { console.info('[debug] tabs changed (raw):', newVal) }
    }, { deep: true })

    // When menus load or change, update any tabs whose title is currently the same as their path
    // to a nicer menu label if available.
    watch(() => menus.value, (m) => {
      try {
        const ms: any[] = m || []
        // helper to find a display title for a path
        function findTitleByPath(nodes: any[], path: string): string | null {
          if (!nodes || !nodes.length) return null
          for (const n of nodes) {
            if (n.path) {
              const np = String(n.path).replace(/\/+$/, '')
              const pp = String(path).replace(/\/+$/, '')
              if (np === pp) {
                if (n.name) {
                  // 使用safeT函数来翻译menu.name
                  return safeT(String(n.name), String(n.name))
                }
                if (n.title) {
                  // 使用safeT函数来翻译menu.title
                  return safeT(String(n.title), String(n.title))
                }
              }
            }
            if (n.children) {
              const f = findTitleByPath(n.children, path)
              if (f) return f
            }
          }
          return null
        }

        // iterate tabs and update where title equals path (likely fallback state)
        tabs.value.forEach((tb: any) => {
          if (!tb || !tb.path) return
          if (tb.title === tb.path || tb.title === String(tb.path)) {
            const nice = findTitleByPath(ms, tb.path)
            if (nice) {
              try { tabsStore.updateTitle(tb.path, nice) } catch (e) {}
            }
          }
        })
      } catch (e) {
        // noop
      }
    }, { immediate: true })

    // open a new tab when route changes (优先 meta.title -> currentTitle -> route.name -> path)
    watch(() => route.fullPath, (newPath) => {
      if (!newPath) return
      let title = ''
      if (route.meta?.title) title = safeT(String(route.meta.title), String(route.meta.title))
      if (!title) title = currentTitle.value
      if (!title && typeof route.name === 'string') title = safeT(String(route.name), String(route.name))
      if (!title) title = String(route.path)
      tabsStore.open(route.path, title)
    })

    function openTab(path: string, title?: string) {
      tabsStore.open(path, title)
    }

    function onTabClick(tab: any) {
      if (tab && tab.props) {
        router.push(tab.props.name)
      }
    }

    function onTabRemove(targetName: string) {
      tabsStore.remove(targetName)
      // if removed was active, navigate to the store's active
      const a = tabsStore.active
      if (a) router.push(a)
    }

    function closeAllTabs() {
      tabsStore.closeAll()
      if (tabsStore.active) router.push(tabsStore.active)
    }

    function closeOtherTabs() {
      tabsStore.closeOthers()
      if (tabsStore.active) router.push(tabsStore.active)
    }

  // language switching is handled on the Login page only

    return { profile, menus, onNavigate, goProfile, logout, currentTitle, isDashboard, breadcrumbs, goBreadcrumb, tabs, activeTab, onTabClick, onTabRemove, closeAllTabs, closeOtherTabs, shortEmail, isLong, t, actionsLabel, closeAllLabel, closeOthersLabel }
  }
})
</script>

<style scoped>
.app-header {
  height:64px;
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:0 20px;
  border-bottom:1px solid var(--el-border-color, #eaeaea);
  background: linear-gradient(90deg, rgba(255,255,255,0.6), rgba(250,250,250,0.6));
  box-shadow: 0 1px 3px rgba(16,24,40,0.04);
}
.header-left { display:flex; align-items:center; gap:12px }

/* 头部品牌区域样式 */
.header-brand {
  display: flex;
  align-items: center;
  margin-right: 16px;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  line-height: 1.2;
}

.user-block { display:flex; flex-direction:column; }
.user-name { font-weight:600; font-size:14px; color: #2b2b2b }
.user-sub { font-size:12px; color:#888 }

.header-right { display:flex; align-items:center }

/* user info truncation: limit width so long emails don't push layout
   show full values via native title on hover; responsive shrink on small screens */
.header-right .user-info {
  max-width: 180px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  box-sizing: border-box;
}
.header-right .user-name,
.header-right .user-sub {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

@media (max-width: 480px) {
  .header-right .user-info { max-width: 120px; }
  .header-right .user-name { font-size:13px }
}

/* marquee / sliding for long user-info: fixed visible width approx 5 Chinese characters */
.marquee {
  display:inline-block;
  max-width: 10ch; /* ~10 characters, adjust if needed; Chinese width may vary */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}
.marquee.is-long:hover {
  /* slide animation on hover to reveal full text */
  animation: marquee-scroll 6s linear forwards;
}
@keyframes marquee-scroll {
  0% { transform: translateX(0); }
  100% { transform: translateX(calc(-100% + 10ch)); }
}


/* make side menu slightly separated */
aside { background: #fafafa }

/* Ensure aside has fixed width and menu content stays inside */
aside { width:220px; box-sizing:border-box; }
.app-side-menu { width:100%; box-sizing:border-box; overflow:auto; flex:1; height:100% }
.app-side-menu .el-menu-item, .app-side-menu .el-sub-menu__title { text-overflow:ellipsis; overflow:hidden; white-space:nowrap }

/* Reserve scrollbar space to prevent layout shift when content changes */
main { -webkit-overflow-scrolling: touch }
.main-layout main { scrollbar-gutter: stable both-edges }

@media (max-width: 768px) {
  .header-right .user-sub { display:none }
}

/* Tabs bar styles */
.tabs-bar {
  border-bottom:1px solid var(--el-border-color, #eaeaea);
  background: #fff;
  padding:6px 16px;
  display:flex;
  align-items:center;
  justify-content:space-between;
}
.tabs-wrapper { position:relative; display:flex; align-items:center; flex:0 1 auto; min-width:0; max-width:calc(100% - 140px) }
.tabs-scroll { max-width:100%; overflow-x:auto; overflow-y:hidden; -webkit-overflow-scrolling:touch; scroll-behavior:smooth }
.tabs-el :deep(.el-tabs__nav) { white-space:nowrap }
.tabs-actions { margin-left:12px; flex:0 0 auto }
.tabs-fade { pointer-events:none; position:absolute; top:0; bottom:0; width:36px }
.tabs-fade.left { left:0; background: linear-gradient(90deg, rgba(255,255,255,1), rgba(255,255,255,0)); }
.tabs-fade.right { right:0; background: linear-gradient(270deg, rgba(255,255,255,1), rgba(255,255,255,0)); }
.tabs-actions .tabs-actions-btn { display:flex; align-items:center; gap:6px; color:var(--el-color-primary); }
.tabs-actions .tabs-actions-btn .el-icon-more { vertical-align:middle }
.tabs-actions .tabs-actions-btn { border-radius:4px; padding:4px 8px }
.tabs-actions .tabs-actions-text { margin-left:6px; font-size:13px }
.tabs-actions .tabs-actions-btn:hover { box-shadow: 0 1px 6px rgba(16,24,40,0.06) }
</style>
