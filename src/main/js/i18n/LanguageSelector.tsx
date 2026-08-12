import { useState, type ChangeEvent } from "react";
import { useTranslation } from 'react-i18next';
import i18n from './i18n';

const LanguageSelector = () => {
  const { t } = useTranslation();
  const [selectedLanguage, setSelectedLanguage] = useState(i18n.language); // currently active language

  const chooseLanguage = (e: ChangeEvent<HTMLSelectElement>) => {
    i18n.changeLanguage(e.target.value);   // switches the active language
    setSelectedLanguage(e.target.value);
    localStorage.setItem("language", e.target.value);
  };

  return (
    // Controlled <select>: its value is driven by state. The aria-label gives it an accessible
    // name for screen readers.
    <select aria-label={t('select_language', 'Select language')} value={selectedLanguage} onChange={chooseLanguage}>
      <option value="en">English</option>
      <option value="nl">Dutch (Nederlands)</option>
      <option value="fr">French (Française)</option>
      <option value="ru">Russian (Русский)</option>
    </select>
  );
};

export default LanguageSelector;
