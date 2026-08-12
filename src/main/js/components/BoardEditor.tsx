import { useRef, useEffect, useState, type MouseEvent } from 'react';
import type { CellDto } from '../types';

interface BoardEditorProps {
  width: number;
  height: number;
  color: string;
  // Which half the current player may fill: player1 (create) = left, player2 (join) = right. Used only to
  // shade the other half; the click validation (toggle vs place, half rule) is done by the parent.
  half: 'left' | 'right';
  cells: CellDto[];
  // Reports the clicked board cell (grid coordinates within the field). The parent decides what to do.
  onCellClick?: (x: number, y: number) => void;
  disabled?: boolean;
}

/**
 * An editable game board: shows the placed cells with the other half shaded, and reports clicks (as grid
 * cells) to the parent, which handles toggling a cell or placing a known object. Like GameCanvas, it sizes
 * itself to fit its parent (preserving the board aspect ratio) and re-fits on resize.
 */
const BoardEditor = ({ width, height, color, half, cells, onCellClick, disabled }: BoardEditorProps) => {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  // Fit the board aspect into the wrapper; recompute whenever the wrapper resizes.
  useEffect(() => {
    const wrapper = wrapperRef.current;
    if (!wrapper) {
      return;
    }
    const recompute = () => {
      const margin = 8; // room for the 2px border + a small gap, so it never clips or overflows
      const availableWidth = wrapper.clientWidth - margin;
      const availableHeight = wrapper.clientHeight - margin;
      if (availableWidth <= 0 || availableHeight <= 0) {
        return;
      }
      const aspect = width / height;
      let w = availableWidth;
      let h = w / aspect;
      if (h > availableHeight) {
        h = availableHeight;
        w = h * aspect;
      }
      setSize({ width: Math.floor(w), height: Math.floor(h) });
    };
    recompute();
    const observer = new ResizeObserver(recompute);
    observer.observe(wrapper);
    return () => observer.disconnect();
  }, [width, height]);

  useEffect(() => {
    const context = canvasRef.current?.getContext('2d');
    if (!context || size.width === 0) {
      return;
    }
    const canvasWidth = size.width;
    const canvasHeight = size.height;
    const cellWidth = canvasWidth / width;
    const cellHeight = canvasHeight / height;
    context.clearRect(0, 0, canvasWidth, canvasHeight);

    // Shade the half the player may NOT fill.
    const midX = (width / 2) * cellWidth;
    context.fillStyle = '#DDDDDD';
    if (half === 'left') {
      context.fillRect(midX, 0, canvasWidth - midX, canvasHeight);
    } else {
      context.fillRect(0, 0, midX, canvasHeight);
    }

    // Placed cells in the player's colour.
    context.fillStyle = color;
    for (const cell of cells) {
      context.fillRect(cell.x * cellWidth, cell.y * cellHeight, cellWidth, cellHeight);
    }

    // Grid.
    context.strokeStyle = '#AAAAAA';
    context.lineWidth = 1;
    for (let i = 0; i <= width; i++) {
      context.beginPath();
      context.moveTo(i * cellWidth, 0);
      context.lineTo(i * cellWidth, canvasHeight);
      context.stroke();
    }
    for (let j = 0; j <= height; j++) {
      context.beginPath();
      context.moveTo(0, j * cellHeight);
      context.lineTo(canvasWidth, j * cellHeight);
      context.stroke();
    }

    // Divider between the two halves.
    context.strokeStyle = '#000000';
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(midX, 0);
    context.lineTo(midX, canvasHeight);
    context.stroke();
  }, [cells, width, height, color, half, size]);

  const handleClick = (event: MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (disabled || !canvas || !onCellClick) {
      return;
    }
    const rect = canvas.getBoundingClientRect();
    const x = Math.floor(((event.clientX - rect.left) / rect.width) * width);
    const y = Math.floor(((event.clientY - rect.top) / rect.height) * height);
    if (x >= 0 && x < width && y >= 0 && y < height) {
      onCellClick(x, y);
    }
  };

  return (
    <div ref={wrapperRef} className="board-wrapper">
      <canvas
        ref={canvasRef}
        width={size.width}
        height={size.height}
        className={disabled ? 'board-canvas' : 'board-canvas clickable'}
        style={{ display: size.width ? 'block' : 'none' }}
        onClick={handleClick}
      />
    </div>
  );
};

export default BoardEditor;
