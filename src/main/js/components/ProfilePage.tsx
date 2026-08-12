import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getJson } from '../http';
import type { ProfileDto } from '../types';

/**
 * The current player's Profile page. For now it shows only stats: a summary line (total games played,
 * games won/lost/drawn) above a bordered table of every game the player has played, newest first, with the
 * opponent, board size, result (from this player's perspective) and finish date. Nick-name editing and
 * other profile fields can be added here later.
 */
const ProfilePage = () => {
  const { t, i18n } = useTranslation();

  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: () => getJson<ProfileDto>('/api/games/profile'),
  });

  if (isLoading || !profile) {
    return <p>{t('loading', 'Loading...')}</p>;
  }

  const games = profile.games ?? [];

  const resultLabel = (result: string) => {
    switch (result) {
      case 'WON':
        return t('result_won', 'Won');
      case 'LOST':
        return t('result_lost', 'Lost');
      case 'DRAW':
        return t('result_draw', 'Draw');
      default:
        return t('result_ongoing', 'Ongoing');
    }
  };

  // Format an ISO instant as a long, localized date-and-time (full month name), respecting the currently
  // selected UI language: e.g. "3 July 2026 at 17:28" (en), "3 juillet 2026 à 17:28" (fr).
  const formatFinishDate = (iso: string) =>
    new Date(iso).toLocaleString(i18n.language, { dateStyle: 'long', timeStyle: 'short' });

  return (
    <div>
      <h2>{t('profile', 'Profile')}</h2>
      <p>{t('nick_name', 'Nick name')}: {profile.nickName}</p>
      <p className="profile-summary">
        <span>{t('total_games_played', 'Total games played')}: {profile.gamesPlayed}</span>
        <span>{t('games_won', 'Games won')}: {profile.gamesWon}</span>
        <span>{t('games_lost', 'Games lost')}: {profile.gamesLost}</span>
        <span>{t('games_drawn', 'Games drawn')}: {profile.gamesDrawn}</span>
      </p>

      {games.length === 0 ? (
        <p>{t('no_games_played', 'You have not played any games yet.')}</p>
      ) : (
        <table className="stats-table">
          <thead>
            <tr>
              <th>{t('opponent_name', 'Opponent name')}</th>
              <th>{t('size', 'Size')}</th>
              <th>{t('result', 'Result')}</th>
              <th>{t('game_finish_date', 'Finish Date')}</th>
            </tr>
          </thead>
          <tbody>
            {games.map((game) => (
              <tr key={game.gameId}>
                <td>{game.opponentNickName ?? '-'}</td>
                <td>
                  {game.width} × {game.height}
                </td>
                <td>{resultLabel(game.result)}</td>
                <td>{game.finishedAt ? formatFinishDate(game.finishedAt) : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default ProfilePage;
