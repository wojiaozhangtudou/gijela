import { createI18n } from 'vue-i18n'
import zh from './locales/zh.json'
import en from './locales/en.json'

export const messages = { zh, en }

function getSavedLocale() {
  try {
    if (typeof window !== 'undefined') {
      const v = localStorage.getItem('locale')
      if (v === 'en' || v === 'zh') return v
    }
  } catch (e) {}
  return 'zh'
}

const i18n = createI18n({
  legacy: false,
  locale: getSavedLocale(),
  fallbackLocale: 'zh',
  globalInjection: true,
  messages
})

export default i18n
