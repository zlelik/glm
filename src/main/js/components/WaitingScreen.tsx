import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { postJson } from '../http';
import stompClient, { type Registration } from '../websocket-listener';
import type { GameHubDto } from '../types';

/**
 * Shown to the game's creator while it waits for a second player. Subscribes to /topic/gameStarted; any
 * such event means a join happened, so it refetches this game's details (and gameHub). If the game now has
 * two players, GameHub advances to the live board; otherwise it stays waiting. The creator can also Exit
 * Game to cancel while waiting.
 */
const WaitingScreen = ({ gameId }: { gameId: number }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  useEffect(() => {
    const onStarted = () => {
      queryClient.invalidateQueries({ queryKey: ['gameDetails', gameId] });
      queryClient.invalidateQueries({ queryKey: ['gameHub'] });
    };
    const routes: Registration[] = [{ route: '/topic/gameStarted', callback: onStarted }];
    stompClient.register(routes);
    return () => {
      stompClient.unregister(routes);
    };
  }, [gameId, queryClient]);

  const exitGame = async () => {
    try {
      await postJson<GameHubDto>(`/api/games/${gameId}/exit`, {});
    } finally {
      queryClient.invalidateQueries({ queryKey: ['gameHub'] });
    }
  };

  return (
    <div>
      <p>{t('waiting_for_second_player', 'Waiting for second player...')}</p>
      <button onClick={exitGame}>{t('exit_game', 'Exit Game')}</button>
    </div>
  );
};

export default WaitingScreen;
