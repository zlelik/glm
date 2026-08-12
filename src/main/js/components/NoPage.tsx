import { useTranslation } from 'react-i18next';

const NoPage = () => {
  const { t } = useTranslation();
  return (
    <h1>{t('page_not_found')}</h1>
  );
};

export default NoPage;
