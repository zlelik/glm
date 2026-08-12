import { useState, useContext, useEffect, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import BoardEditor from './BoardEditor';
import { UndoIcon } from './UndoIcon';
import { UserContext } from './UserProvider';
import { KNOWN_OBJECTS, rotateObject } from '../knownObjects';
import { postJson } from '../http';
import log, { ts } from '../logger';
import type { CellDto, GameOptions, GameSize, JoinableGame, GameHubDto } from '../types';

interface GameFormProps {
  mode: 'create' | 'join';
  options: GameOptions;
  // Provided in join mode: the game being joined (its size is fixed, the player fills the right half).
  game?: JoinableGame;
  onSubmitted: () => void;
  onCancel: () => void;
}

/**
 * Shared create/join form: pick a colour, (create only) a board size, place your starting cells on your
 * half, then submit. You can draw single cells or pick a Known Object (Glider, spaceships, gun, ...) and
 * click to drop it (top-left at the click). Ctrl/Cmd+Z undoes the last cell or object placement.
 */
const GameForm = ({ mode, options, game, onSubmitted, onCancel }: GameFormProps) => {
  const { t } = useTranslation();
  const { currentUser } = useContext(UserContext);
  const isJoin = mode === 'join';
  const half = isJoin ? 'right' : 'left';
  const playerId = currentUser?.id ?? 0;

  // When joining, the creator's colour is taken: drop it from the choices, and pick a different default.
  const takenColor = isJoin ? game?.player1.color : undefined;
  const availableColors = options.colors.filter((c) => c.value !== takenColor);
  const initialColor =
    takenColor === options.defaultColor ? availableColors[0]?.value ?? options.defaultColor : options.defaultColor;

  const [color, setColor] = useState<string>(initialColor);
  const [size, setSize] = useState<GameSize>(
    isJoin && game ? { width: game.width, height: game.height, label: '' } : options.defaultSize,
  );
  const [cells, setCells] = useState<CellDto[]>([]);
  const [history, setHistory] = useState<CellDto[][]>([]); // snapshots for undo
  const [selectedObjectKey, setSelectedObjectKey] = useState<string>('');
  const [rotation, setRotation] = useState(0); // number of 90° clockwise turns applied to the object
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Refs so applyCells/undo stay stable (for the keydown listener) while always seeing the latest state.
  const cellsRef = useRef(cells);
  cellsRef.current = cells;
  const historyRef = useRef(history);
  historyRef.current = history;

  const applyCells = useCallback((next: CellDto[]) => {
    setHistory([...historyRef.current, cellsRef.current]);
    setCells(next);
  }, []);

  const undo = useCallback(() => {
    const h = historyRef.current;
    if (h.length === 0) {
      return;
    }
    setCells(h[h.length - 1]);
    setHistory(h.slice(0, -1));
  }, []);

  // Ctrl/Cmd+Z undoes the last placement (a single cell or a whole object).
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && (e.key === 'z' || e.key === 'Z')) {
        e.preventDefault();
        undo();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [undo]);

  const isAllowedX = (x: number) => (half === 'left' ? 2 * x < size.width : 2 * x >= size.width);

  // Known Objects that fit the current player's half (width) and the board height. Depends on size + half,
  // so the list changes with the chosen board.
  const halfWidth = half === 'left' ? Math.ceil(size.width / 2) : Math.floor(size.width / 2);
  const availableObjects = KNOWN_OBJECTS.filter((o) => o.width <= halfWidth && o.height <= size.height);

  const handleSizeChange = (value: string) => {
    const next = options.sizes.find((s) => `${s.width}x${s.height}` === value);
    if (next) {
      setSize(next);
      setCells([]); // placed cells no longer fit the new board
      setHistory([]);
      setSelectedObjectKey('');
      setRotation(0);
    }
  };

  const handleBoardClick = (x: number, y: number) => {
    if (submitting) {
      return;
    }
    const object = selectedObjectKey ? KNOWN_OBJECTS.find((o) => o.key === selectedObjectKey) : null;
    if (object) {
      // Place the object (in its current rotation) with its top-left at the clicked cell, only if every
      // cell fits the field + half.
      const rotated = rotateObject(object, rotation);
      const placed = rotated.cells.map((c) => ({ x: x + c.x, y: y + c.y, playerId }));
      const fits = placed.every(
        (c) => c.x >= 0 && c.x < size.width && c.y >= 0 && c.y < size.height && isAllowedX(c.x),
      );
      if (!fits) {
        return; // doesn't fit here - click elsewhere
      }
      const seen = new Set(cells.map((c) => `${c.x},${c.y}`));
      const merged = [...cells];
      for (const c of placed) {
        const key = `${c.x},${c.y}`;
        if (!seen.has(key)) {
          seen.add(key);
          merged.push(c);
        }
      }
      applyCells(merged);
    } else {
      // Draw mode: toggle a single cell on the allowed half.
      if (!isAllowedX(x)) {
        return;
      }
      const exists = cells.some((c) => c.x === x && c.y === y);
      applyCells(exists ? cells.filter((c) => !(c.x === x && c.y === y)) : [...cells, { x, y, playerId }]);
    }
  };

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      if (isJoin && game) {
        await postJson<GameHubDto>(`/api/games/${game.gameId}/join`, { color, cells });
      } else {
        await postJson<GameHubDto>('/api/games', { color, width: size.width, height: size.height, cells });
      }
      onSubmitted();
    } catch (e) {
      log.warn(ts(), 'game submit failed', e);
      setError(e instanceof Error ? e.message : String(e));
      setSubmitting(false);
    }
  };

  return (
    <div className="game-form">
      <div>
        <label>
          {t('color', 'Color')}:{' '}
          <select
            value={color}
            disabled={submitting}
            onChange={(e) => setColor(e.target.value)}
            style={{ backgroundColor: color }}
          >
            {availableColors.map((c) => (
              <option key={c.value} value={c.value} style={{ backgroundColor: c.value }}>
                {t(`color_${c.key}`)}
              </option>
            ))}
          </select>
        </label>{' '}
        <label>
          {t('size', 'Size')}:{' '}
          <select
            value={`${size.width}x${size.height}`}
            disabled={isJoin || submitting}
            onChange={(e) => handleSizeChange(e.target.value)}
          >
            {options.sizes.map((s) => (
              <option key={s.label} value={`${s.width}x${s.height}`}>
                {s.label}
              </option>
            ))}
          </select>
        </label>{' '}
        <label>
          {t('known_objects', 'Known Objects')}:{' '}
          <select
            value={selectedObjectKey}
            disabled={submitting}
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
        </label>{' '}
        <label>
          {t('rotation', 'Rotation')}:{' '}
          <select
            value={rotation}
            disabled={submitting || !selectedObjectKey}
            onChange={(e) => setRotation(Number(e.target.value))}
          >
            {[0, 1, 2, 3].map((turns) => (
              <option key={turns} value={turns}>
                {turns * 90}°
              </option>
            ))}
          </select>
        </label>{' '}
        <button
          type="button"
          className="icon-button"
          onClick={undo}
          disabled={submitting || history.length === 0}
          aria-label={t('undo', 'Undo (Ctrl+Z)')}
          title={t('undo', 'Undo (Ctrl+Z)')}
        >
          <UndoIcon />
        </button>
      </div>

      <div className="game-form-board">
        <BoardEditor
          width={size.width}
          height={size.height}
          color={color}
          half={half}
          cells={cells}
          onCellClick={handleBoardClick}
          disabled={submitting}
        />
      </div>

      <div>
        <button onClick={submit} disabled={submitting}>
          {isJoin ? t('join_game', 'Join Game') : t('create_game', 'Create Game')}
        </button>{' '}
        <button onClick={onCancel} disabled={submitting}>
          {t('cancel', 'Cancel')}
        </button>
        {error && <span className="error-text" style={{ marginLeft: '8px' }}>{error}</span>}
      </div>
    </div>
  );
};

export default GameForm;
