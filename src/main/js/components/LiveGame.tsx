import { useEffect, useContext, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import GameCanvas from './GameCanvas';
import GameStats from './GameStats';
import { FullscreenEnterIcon, FullscreenExitIcon } from './FullscreenIcons';
import { UserContext } from './UserProvider';
import { KNOWN_OBJECTS, rotateObject } from '../knownObjects';
import { postJson } from '../http';
import log, { ts } from '../logger';
import stompClient, { type Registration } from '../websocket-listener';
import type { CellDto, GameDetailsDto, GameHubDto } from '../types';

interface LiveGameProps {
  gameId: number;
  details: GameDetailsDto;
  // Called when the game finishes (a player exited, or it was won/drawn). The parent decides what to show
  // (e.g. a win/lose popup over the final board) and when to leave the game.
  onGameFinished: () => void;
}

/**
 * A running game (both players present): renders the live board, refetches it on each cell-change event,
 * and offers an Exit Game button. When the game finishes it notifies the parent (which keeps the final
 * board on screen under a result popup) rather than leaving immediately.
 *
 * During play a player may add cells to their own half by clicking. Each iteration grants one "accumulated
 * cell" credit; placing costs one credit per cell. In draw mode a click adds a single cell (1 credit); pick
 * a Known Object (+ rotation) to drop its whole shape at once (costing one credit per cell), which is why a
 * player must save up credits by waiting a few ticks before placing a multi-cell object.
 */
const LiveGame = ({ gameId, details, onGameFinished }: LiveGameProps) => {
  const { t } = useTranslation();
  const { currentUser } = useContext(UserContext);
  const queryClient = useQueryClient();

  const [selectedObjectKey, setSelectedObjectKey] = useState<string>('');
  const [rotation, setRotation] = useState(0); // number of 90° clockwise turns applied to the object

  // Which player the current user is, and therefore how many credits they have. During the live game a
  // player may place anywhere on the board (not just their own half), so no half restriction here.
  const isPlayer1 = details.player1?.id === currentUser?.id;
  const playerId = currentUser?.id ?? 0;
  const accumulated = isPlayer1 ? details.player1AccumulatedCells : details.player2AccumulatedCells;

  // Known Objects that fit anywhere on the board (the whole field is placeable during the live game).
  const availableObjects = KNOWN_OBJECTS.filter((o) => o.width <= details.width && o.height <= details.height);

  // Fullscreen: request it on the game container, so only that element (stats + board + the exit-fullscreen
  // icon) is shown - the nav/headers, which live outside it, are hidden by the browser automatically.
  const containerRef = useRef<HTMLDivElement>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const onChange = () => setIsFullscreen(document.fullscreenElement === containerRef.current);
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);

  const toggleFullscreen = () => {
    const action = isFullscreen ? document.exitFullscreen() : containerRef.current?.requestFullscreen();
    Promise.resolve(action).catch((e) => log.warn(ts(), 'fullscreen toggle failed', e));
  };

  useEffect(() => {
    const refetchGame = () => queryClient.invalidateQueries({ queryKey: ['gameDetails', gameId] });
    const routes: Registration[] = [
      { route: '/topic/newCell', callback: refetchGame },
      { route: '/topic/deleteCell', callback: refetchGame },
      { route: '/topic/gameFinished', callback: onGameFinished },
    ];
    stompClient.register(routes);
    return () => {
      stompClient.unregister(routes);
    };
  }, [gameId, queryClient, onGameFinished]);

  const exitGame = async () => {
    try {
      await postJson<GameHubDto>(`/api/games/${gameId}/exit`, {});
    } finally {
      queryClient.invalidateQueries({ queryKey: ['gameHub'] });
    }
  };

  // Post one or more cells to the server; it charges one credit per newly created cell and refetches. If the
  // server rejects it (e.g. not enough credits due to a race) we just log and refetch - no disruptive popup.
  const postCells = async (toPlace: CellDto[]) => {
    try {
      await postJson(`/api/games/${gameId}/cells`, toPlace);
    } catch (e) {
      log.warn(ts(), 'place cells failed', e);
    } finally {
      queryClient.invalidateQueries({ queryKey: ['gameDetails', gameId] });
    }
  };

  // Click handler: in draw mode add a single cell; with an object selected, drop the whole (rotated) shape.
  // If it does not fit the board or there are not enough accumulated credits, silently do nothing.
  const handleBoardClick = (x: number, y: number) => {
    const object = selectedObjectKey ? KNOWN_OBJECTS.find((o) => o.key === selectedObjectKey) : null;
    if (object) {
      const rotated = rotateObject(object, rotation);
      const placed = rotated.cells.map((c) => ({ x: x + c.x, y: y + c.y, playerId }));
      const fits = placed.every((c) => c.x >= 0 && c.x < details.width && c.y >= 0 && c.y < details.height);
      if (!fits || placed.length > accumulated) {
        return; // doesn't fit here, or not enough credits - click elsewhere / wait
      }
      postCells(placed);
    } else {
      if (accumulated < 1) {
        return; // not enough credits yet
      }
      postCells([{ x, y, playerId }]);
    }
  };

  // Flex column filling the available height: the toolbar (stats + fullscreen), the object controls, stats
  // and Exit take their natural height, the canvas area gets the rest (flex:1, minHeight:0) and GameCanvas
  // fits the board into it. The Exit Game button is hidden in fullscreen; the object controls stay visible.
  return (
    <div ref={containerRef} className="game">
      <div className="game-toolbar">
        <GameStats details={details} currentUserId={currentUser?.id ?? null} />
        <button
          onClick={toggleFullscreen}
          className="icon-button"
          aria-label={isFullscreen ? t('exit_fullscreen', 'Exit full screen') : t('enter_fullscreen', 'Enter full screen')}
        >
          {isFullscreen ? <FullscreenExitIcon /> : <FullscreenEnterIcon />}
        </button>
      </div>
      <div className="game-toolbar">
        <span>
          {t('accumulated_cells', 'Accumulated cells')}: {accumulated}
        </span>
        <label>
          {t('known_objects', 'Known Objects')}:{' '}
          <select
            value={selectedObjectKey}
            onChange={(e) => {
              setSelectedObjectKey(e.target.value);
              setRotation(0);
            }}
          >
            <option value="">{t('draw_cells', 'Draw single cells')}</option>
            {availableObjects.map((o) => (
              <option key={o.key} value={o.key}>
                {t(`object_${o.key}`, o.key)}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('rotation', 'Rotation')}:{' '}
          <select
            value={rotation}
            disabled={!selectedObjectKey}
            onChange={(e) => setRotation(Number(e.target.value))}
          >
            {[0, 1, 2, 3].map((turns) => (
              <option key={turns} value={turns}>
                {turns * 90}°
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="game-canvas-area">
        <GameCanvas gameDetails={details} onCellClick={handleBoardClick} />
      </div>
      {!isFullscreen && (
        <div>
          <button onClick={exitGame}>{t('exit_game', 'Exit Game')}</button>
        </div>
      )}
    </div>
  );
};

export default LiveGame;
