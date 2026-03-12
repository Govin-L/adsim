import { useTranslation } from 'react-i18next'
import { changeLanguage } from '../i18n'

export default function LanguageSwitch() {
  const { i18n } = useTranslation()
  const isZh = i18n.language === 'zh'

  return (
    <button
      onClick={() => changeLanguage(isZh ? 'en' : 'zh')}
      className="text-sm text-gray-400 hover:text-gray-600 transition-colors"
    >
      {isZh ? 'EN' : '中文'}
    </button>
  )
}
