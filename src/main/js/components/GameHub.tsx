import { useRef, useState, useContext, useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getJson } from '../http';
import { UserContext } from './UserProvider';
import GameLobby from './GameLobby';
import WaitingScreen from './WaitingScreen';
import LiveGame from './LiveGame';
import MessagePopup from './MessagePopup';
import log, { ts } from '../logger';
import type { GameDetailsDto, GameResultDto, GameHubDto } from '../types';

const DRAW_WINNER_ID = -1;

/**
 * Entry point for the "Game Hub". The 'gameHub' query drives the whole view:
 *  - no game        -> GameLobby (create or search/join)
 *  - game, waiting  -> WaitingScreen (player2 not joined yet)
 *  - game, running  -> LiveGame (both players present)
 * Creating/joining invalidates these queries, so the view advances automatically. When the running game
 * finishes, we show a win/lose/draw popup OVER the final board (we only leave the game on OK).
 */
const GameHub = () => {
  const { t } = useTranslation();
  const { currentUser } = useContext(UserContext);
  const queryClient = useQueryClient();

  const { data: gameHub, isLoading: gameHubLoading } = useQuery({
    queryKey: ['gameHub'],
    queryFn: () => getJson<GameHubDto>('/api/games/my'),
  });

  const gameId = gameHub?.gameId ?? null;

  const { data: details, isLoading: detailsLoading } = useQuery({
    queryKey: ['gameDetails', gameId],
    queryFn: () => getJson<GameDetailsDto>(`/api/games/${gameId}/details`),
    enabled: gameId != null,
  });

  const [resultMessage, setResultMessage] = useState<string | null>(null);

  // Keep current gameId/user in refs so the gameFinished handler stays stable (avoids STOMP re-subscribes).
  const gameIdRef = useRef<number | null>(gameId);
  gameIdRef.current = gameId;
  const currentUserRef = useRef(currentUser);
  currentUserRef.current = currentUser;

  // Called when the running game finishes. Fetch its result; if there is a winner, show a win/lose/draw
  // popup but DO NOT leave the game yet, so the final board stays visible beneath the popup. If there is no
  // winner (a player exited), just return to the lobby.
  const handleGameFinished = useCallback(() => {
    const id = gameIdRef.current;
    if (id == null) {
      return;
    }
    getJson<GameResultDto>(`/api/games/${id}/result`)
      .then((result) => {
        if (result.status !== 'FINISHED') {
          return;
        }
        if (result.winnerId == null) {
          queryClient.invalidateQueries({ queryKey: ['gameHub'] });
          return;
        }
        // Refetch the final, persisted board so the player sees the actual last state under the popup.
        queryClient.invalidateQueries({ queryKey: ['gameDetails', id] });
        const user = currentUserRef.current;
        if (result.winnerId === DRAW_WINNER_ID) {
          setResultMessage(t('draw', "It's a draw"));
        } else if (user && result.winnerId === user.id) {
          setResultMessage(t('you_win', 'You win!'));
        } else {
          setResultMessage(t('you_lose', 'You lose'));
        }
      })
      .catch((e) => log.warn(ts(), 'failed to fetch game result', e));
  }, [queryClient, t]);

  // OK on the popup: dismiss it AND now leave the finished game (back to the lobby).
  const closeResult = () => {
    setResultMessage(null);
    queryClient.invalidateQueries({ queryKey: ['gameHub'] });
  };

  let content;
  if (gameHubLoading) {
    content = <p>{t('loading', 'Loading...')}</p>;
  } else if (gameId == null) {
    content = <GameLobby />;
  } else if (detailsLoading || !details) {
    content = <p>{t('loading', 'Loading...')}</p>;
  } else if (details.player2 == null) {
    content = <WaitingScreen gameId={gameId} />;
  } else {
    content = <LiveGame gameId={gameId} details={details} onGameFinished={handleGameFinished} />;
  }

  return (
    <div className="full-height">
      {content}
      {resultMessage && <MessagePopup message={resultMessage} onClose={closeResult} />}
    </div>
  );
};

export default GameHub;
