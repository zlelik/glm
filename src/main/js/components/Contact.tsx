import { useTranslation } from 'react-i18next';

const Contact = () => {
  const { t } = useTranslation();
  return (
    <h1>{t('contact_details')}</h1>
  );
};

export default Contact;
