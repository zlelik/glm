import { useTranslation } from 'react-i18next';

/**
 * A minimal modal message box (overlay + message + dismiss button), used instead of window.alert.
 * Deliberately self-contained and inline-styled: to switch to a nicer widget later, replace ONLY this
 * component's internals with a MUI <Dialog>/<Snackbar> - the props (message, onClose) stay the same.
 */
const MessagePopup = ({ message, onClose }: { message: string; onClose: () => void }) => {
  const { t } = useTranslation();
  // The overlay intentionally has NO click handler: the popup closes only via the OK button.
  return (
    <div className="popup-overlay">
      <div className="popup-box">
        <p>{message}</p>
        <button onClick={onClose}>{t('ok', 'OK')}</button>
      </div>
    </div>
  );
};

export default MessagePopup;
