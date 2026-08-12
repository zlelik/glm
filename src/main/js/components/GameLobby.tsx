import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getJson } from '../http';
import GameForm from './GameForm';
import type { GameOptions, JoinableGame } from '../types';

type View = 'menu' | 'create' | 'search';

// Table of games waiting for a second player, each with a Join button.
const JoinTable = ({
  onSelect,
  onBack,
}: {
  onSelect: (game: JoinableGame) => void;
  onBack: () => void;
}) => {
  const { t } = useTranslation();
  const { data: games, isLoading } = useQuery({
    queryKey: ['joinableGames'],
    queryFn: () => getJson<JoinableGame[]>('/api/games/joinable'),
  });

  if (isLoading) {
    return <p>{t('loading', 'Loading...')}</p>;
  }
  const list = games ?? [];

  return (
    <div>
      {list.length === 0 ? (
        <p>{t('no_joinable_games', 'No games are waiting for a player right now.')}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>{t('player', 'Player')}</th>
              <th>{t('color', 'Color')}</th>
              <th>{t('size', 'Size')}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {list.map((game) => (
              <tr key={game.gameId}>
                <td>{game.player1.nickName}</td>
                <td>
                  <span className="color-swatch" style={{ backgroundColor: game.player1.color }} />
                </td>
                <td>
                  {game.width} × {game.height}
                </td>
                <td>
                  <button onClick={() => onSelect(game)}>{t('join_game', 'Join Game')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <button onClick={onBack}>{t('back', 'Back')}</button>
    </div>
  );
};

/**
 * Shown when the player has no game: offers Create Game / Search Existing Game, and hosts the create form
 * and the join flow (search table -> join form). On success it invalidates the 'gameHub' query so GameHub
 * re-renders into the waiting/live view.
 */
const GameLobby = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [view, setView] = useState<View>('menu');
  const [joinTarget, setJoinTarget] = useState<JoinableGame | null>(null);

  const { data: options } = useQuery({
    queryKey: ['gameOptions'],
    queryFn: () => getJson<GameOptions>('/api/games/options'),
  });

  const refreshGameHub = () => {
    queryClient.invalidateQueries({ queryKey: ['gameHub'] });
  };

  if (view === 'menu') {
    return (
      <div>
        <button onClick={() => setView('create')}>{t('create_game', 'Create Game')}</button>{' '}
        <button onClick={() => setView('search')}>
          {t('search_existing_game', 'Search Existing Game')}
        </button>
      </div>
    );
  }

  if (!options) {
    return <p>{t('loading', 'Loading...')}</p>;
  }

  if (view === 'create') {
    return (
      <GameForm mode="create" options={options} onSubmitted={refreshGameHub} onCancel={() => setView('menu')} />
    );
  }

  // view === 'search'
  if (joinTarget) {
    return (
      <GameForm
        mode="join"
        options={options}
        game={joinTarget}
        onSubmitted={refreshGameHub}
        onCancel={() => setJoinTarget(null)}
      />
    );
  }
  return <JoinTable onSelect={setJoinTarget} onBack={() => setView('menu')} />;
};

export default GameLobby;
