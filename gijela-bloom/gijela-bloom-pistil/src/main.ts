import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import App from './App.vue'
import router from './router'
import i18n from './i18n'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

function getSavedLocale() {
	try { return localStorage.getItem('locale') || 'zh' } catch { return 'zh' }
}
const saved = getSavedLocale()
const elLocale = saved === 'en' ? en : zhCn
app.use(ElementPlus, { locale: elLocale })
app.use(i18n)

// v-permission directive: usage v-permission="'user:create'" or v-permission="['user:create','user:update']"
import { hasPermission } from './utils/permission'
import { useUserStore } from './store/user'

app.directive('permission', {
	beforeMount(el, binding) {
		try {
			const val = binding.value
			const store = useUserStore()
			if (!val) return
			const evaluate = () => {
				const ok = Array.isArray(val) ? (val as any).some((p: string) => hasPermission(p)) : hasPermission(String(val))
				;(el as HTMLElement).style.display = ok ? '' : 'none'
			}
			// initial
			evaluate()
			// subscribe to store changes so when menus load we re-evaluate
			const unsub = store.$subscribe(() => { evaluate() })
			;(el as any).__vPermission_unsub = unsub
		} catch (e) {
			// noop
		}
	},
	unmounted(el) {
		try {
			const unsub = (el as any).__vPermission_unsub
			if (typeof unsub === 'function') unsub()
		} catch (e) {}
	}
})

app.mount('#app')
