import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import translationsInEn from './locales/en/translation.json';
import translationsInNl from './locales/nl/translation.json';
import translationsInFr from './locales/fr/translation.json';
import translationsInRu from './locales/ru/translation.json';

const resources = {
  en: {
    translation: translationsInEn
  },
  nl: {
    translation: translationsInNl
  },
  fr: {
    translation: translationsInFr
  },
  ru: {
    translation: translationsInRu
  },
};

i18n
  .use(LanguageDetector) // detects the language from localStorage, then the browser
  .use(initReactI18next) // passes i18n down to react-i18next
  .init({
    resources, // resources are important to load translations for the languages.
    // The detector reads the cached language choice from localStorage (under the "language" key) and
    // falls back to the browser language, then to fallbackLng.
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: "language",
      caches: ["localStorage"]
    },
    debug: import.meta.env.DEV, // verbose i18next logging only during development
    fallbackLng: "en", // used when the detected language is not available
    interpolation: {
      escapeValue: false
    },
    ns: "translation", // namespaces help to divide huge translations into multiple small files.
    defaultNS: "translation"
  });

export default i18n;
