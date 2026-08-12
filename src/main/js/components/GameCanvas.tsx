import { useEffect, useRef, useState, type MouseEvent } from 'react';
import type { GameDetailsDto } from '../types';
import log, { ts } from '../logger';

interface GameCanvasProps {
  gameDetails: GameDetailsDto | null;
  // Optional: called with the clicked board cell (grid coordinates) when the player clicks the canvas.
  onCellClick?: (x: number, y: number) => void;
}

/**
 * The game board canvas. It sizes itself to the largest rectangle that fits its parent while preserving the
 * board's aspect ratio, and re-fits on any parent/viewport resize (window resize, device rotation,
 * fullscreen toggle) via a ResizeObserver - so the board always fits the available space without scrolling.
 */
const GameCanvas = ({ gameDetails, onCellClick }: GameCanvasProps) => {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  const boardWidth = gameDetails?.width ?? 1;
  const boardHeight = gameDetails?.height ?? 1;

  // Fit the board aspect ratio into the wrapper; recompute whenever the wrapper resizes.
  useEffect(() => {
    const wrapper = wrapperRef.current;
    if (!wrapper) {
      return;
    }
    const recompute = () => {
      // Leave a small margin so the 2px border is never clipped (e.g. at the screen edge in fullscreen) and
      // the canvas + border never exceed the wrapper (which would force a scrollbar).
      const margin = 8;
      const availableWidth = wrapper.clientWidth - margin;
      const availableHeight = wrapper.clientHeight - margin;
      if (availableWidth <= 0 || availableHeight <= 0) {
        return;
      }
      const aspect = boardWidth / boardHeight;
      let width = availableWidth;
      let height = width / aspect;
      if (height > availableHeight) {
        height = availableHeight;
        width = height * aspect;
      }
      setSize({ width: Math.floor(width), height: Math.floor(height) });
    };
    recompute();
    const observer = new ResizeObserver(recompute);
    observer.observe(wrapper);
    return () => observer.disconnect();
  }, [boardWidth, boardHeight]);

  // Draw the board whenever the size or the game state changes.
  useEffect(() => {
    log.debug(ts(), 'refresh canvas', size);
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (!context || size.width === 0 || !gameDetails) {
      return;
    }
    const { width, height } = size;
    const cellWidth = width / gameDetails.width;
    const cellHeight = height / gameDetails.height;

    context.clearRect(0, 0, width, height);

    // Map of player id -> colour. A game may not have both players yet (waiting), so guard before reading.
    const playerColorMap: Record<number, string> = {};
    if (gameDetails.player1) {
      playerColorMap[gameDetails.player1.id] = gameDetails.player1.color;
    }
    if (gameDetails.player2) {
      playerColorMap[gameDetails.player2.id] = gameDetails.player2.color;
    }
    for (const cell of gameDetails.cells ?? []) {
      const color = playerColorMap[cell.playerId];
      if (!color) {
        continue;
      }
      context.fillStyle = color;
      context.fillRect(cell.x * cellWidth, cell.y * cellHeight, cellWidth, cellHeight);
    }

    // Grid lines.
    context.strokeStyle = '#AAAAAA';
    context.lineWidth = 1;
    for (let i = 0; i <= gameDetails.width; i++) {
      context.beginPath();
      context.moveTo(i * cellWidth, 0);
      context.lineTo(i * cellWidth, height);
      context.stroke();
    }
    for (let j = 0; j <= gameDetails.height; j++) {
      context.beginPath();
      context.moveTo(0, j * cellHeight);
      context.lineTo(width, j * cellHeight);
      context.stroke();
    }
  }, [size, gameDetails]);

  const handleClick = (event: MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!onCellClick || !canvas || !gameDetails) {
      return;
    }
    // Use the rendered size (rect) so the mapping is correct at any canvas size.
    const rect = canvas.getBoundingClientRect();
    const x = Math.floor(((event.clientX - rect.left) / rect.width) * gameDetails.width);
    const y = Math.floor(((event.clientY - rect.top) / rect.height) * gameDetails.height);
    if (x >= 0 && x < gameDetails.width && y >= 0 && y < gameDetails.height) {
      onCellClick(x, y);
    }
  };

  return (
    <div ref={wrapperRef} className="board-wrapper">
      <canvas
        ref={canvasRef}
        width={size.width}
        height={size.height}
        className={onCellClick ? 'board-canvas clickable' : 'board-canvas'}
        style={{ display: size.width ? 'block' : 'none' }}
        onClick={handleClick}
      />
    </div>
  );
};

export default GameCanvas;
