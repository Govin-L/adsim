import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en.json'
import zh from './locales/zh.json'

const savedLang = localStorage.getItem('adsim-lang') || navigator.language.startsWith('zh') ? 'zh' : 'en'

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    zh: { translation: zh },
  },
  lng: savedLang,
  fallbackLng: 'en',
  interpolation: {
    escapeValue: false,
  },
})

export function changeLanguage(lang: string) {
  localStorage.setItem('adsim-lang', lang)
  i18n.changeLanguage(lang)
}

export default i18n
