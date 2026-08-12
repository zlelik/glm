import { useTranslation } from 'react-i18next';
import type { GameDetailsDto } from '../types';

/**
 * One-line stats shown above the board during an active game: the opponent's name and each player's live
 * cell count. The opponent is whichever player is not the current user; counts come from the live cells.
 */
const GameStats = ({ details, currentUserId }: { details: GameDetailsDto; currentUserId: number | null }) => {
  const { t } = useTranslation();

  const opponent =
    details.player1 && currentUserId != null && details.player1.id === currentUserId
      ? details.player2
      : details.player1;

  const cells = details.cells ?? [];
  const myCells = cells.filter((c) => c.playerId === currentUserId).length;
  const opponentCells = opponent ? cells.filter((c) => c.playerId === opponent.id).length : 0;

  return (
    <div>
      {t('opponent_name', 'Opponent name')}: {opponent?.nickName ?? '-'}.{' '}
      {t('your_live_cells', 'Your live cells')}: {myCells}.{' '}
      {t('opponent_live_cells', 'Opponent live cells')}: {opponentCells}
    </div>
  );
};

export default GameStats;
