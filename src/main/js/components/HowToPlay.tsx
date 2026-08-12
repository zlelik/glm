import { useTranslation } from 'react-i18next';

const HowToPlay = () => {
  const { t } = useTranslation();
  return (
    <div>
      <h1>{t('how_to_play_explanation')}</h1>
      <img src="/img/animation1.gif" alt={t('how_to_play_animation_alt', 'Animation showing how the game is played')} />
    </div>
  );
};

export default HowToPlay;
