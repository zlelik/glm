// Known Conway's Game of Life objects, offered in the create/join form. Each is defined as a readable
// plaintext grid ('O' = live cell, anything else = dead) and parsed to relative cell coordinates. The
// width is the longest row and the height is the number of rows. Add more objects by appending a plaintext
// entry below - they will appear in the dropdown automatically wherever they fit a player's half.

export interface KnownObject {
  key: string;
  width: number;
  height: number;
  // Cells relative to the object's top-left corner (0,0).
  cells: { x: number; y: number }[];
}

/**
 * Returns the object's cells rotated 90° clockwise `quarterTurns` times, with the (swapped) bounding box.
 * Used to orient a placed object (e.g. so a gun fires the other way).
 */
export function rotateObject(
  object: KnownObject,
  quarterTurns: number,
): { cells: { x: number; y: number }[]; width: number; height: number } {
  let cells = object.cells;
  let width = object.width;
  let height = object.height;
  const turns = ((quarterTurns % 4) + 4) % 4;
  for (let i = 0; i < turns; i++) {
    // 90° clockwise: (x, y) -> (height - 1 - y, x); width and height swap.
    cells = cells.map((c) => ({ x: height - 1 - c.y, y: c.x }));
    const previousWidth = width;
    width = height;
    height = previousWidth;
  }
  return { cells, width, height };
}

/**
 * Parses a pattern in Golly/LifeWiki RLE body format (tokens: `<n>b` dead cells, `<n>o` live cells,
 * `<n>$` end-of-row, `!` end; no count means 1). All other characters (whitespace, line wraps, the
 * `x = ..` header) are ignored, so the multi-line RLE can be pasted verbatim. Bounding box is derived
 * from the live cells. Used for the larger puffers/rakes, which are far more compact as RLE than as a grid.
 */
function parseRle(key: string, rle: string): KnownObject {
  const cells: { x: number; y: number }[] = [];
  let x = 0;
  let y = 0;
  let count = 0;
  for (const ch of rle) {
    if (ch >= '0' && ch <= '9') {
      count = count * 10 + (ch.charCodeAt(0) - 48);
    } else if (ch === 'b') {
      x += count || 1;
      count = 0;
    } else if (ch === 'o') {
      const run = count || 1;
      for (let i = 0; i < run; i++) {
        cells.push({ x: x + i, y });
      }
      x += run;
      count = 0;
    } else if (ch === '$') {
      y += count || 1;
      x = 0;
      count = 0;
    } else if (ch === '!') {
      break;
    }
  }
  let width = 0;
  let height = 0;
  for (const c of cells) {
    width = Math.max(width, c.x + 1);
    height = Math.max(height, c.y + 1);
  }
  return { key, width, height, cells };
}

function parse(key: string, plaintext: string): KnownObject {
  const rows = plaintext.replace(/^\n/, '').replace(/\n$/, '').split('\n');
  const cells: { x: number; y: number }[] = [];
  let width = 0;
  rows.forEach((row, y) => {
    width = Math.max(width, row.length);
    for (let x = 0; x < row.length; x++) {
      if (row[x] === 'O') {
        cells.push({ x, y });
      }
    }
  });
  return { key, width, height: rows.length, cells };
}

export const KNOWN_OBJECTS: KnownObject[] = [
  parse(
    'glider',
    `
.O.
..O
OOO`,
  ),
  parse(
    'lwss',
    `
.O..O
O....
O...O
OOOO.`,
  ),
  parse(
    'mwss',
    `
...O..
.O...O
O.....
O....O
OOOOO.`,
  ),
  parse(
    'hwss',
    `
...OO..
.O....O
O......
O.....O
OOOOOO.`,
  ),
  parse(
    'gosper_glider_gun',
    `
........................O...........
......................O.O...........
............OO......OO............OO
...........O...O....OO............OO
OO........O.....O...OO..............
OO........O...O.OO....O.O...........
..........O.....O.......O...........
...........O...O...................
............OO.....................`,
  ),
  // Puffer trains and rakes (RLE bodies). These leave a trail of debris / release spaceships as they move.
  parseRle(
    'puffer1',
    `b3o6bo5bo6b3ob$o2bo5b3o3b3o5bo2bo$3bo4b2obo3bob2o4bo3b$3bo19bo3b$3bo2b
o13bo2bo3b$3bo2b2o11b2o2bo3b$2bo3b2o11b2o3bo!`,
  ),
  parseRle(
    'puffer2',
    `b3o11b3o$o2bo10bo2bo$3bo4b3o6bo$3bo4bo2bo5bo$2bo4bo8bo!`,
  ),
  parseRle(
    'blinker_puffer_1',
    `3bo5b$bo3bo3b$o8b$o4bo3b$5o4b4$b2o6b$2ob3o3b$b4o4b$2b2o5b2$5b2o2b$3bo
4bo$2bo6b$2bo5bo$2b6o!`,
  ),
  parseRle(
    'blinker_puffer_2',
    `13b3ob$12b5o$11b2ob3o$12b2o3b3$9bobo5b$2bo5bo2bo5b$b5o3bobo5b$2o3b2ob
2o7b$bo7bo7b$2b2o2bo2bo7b$10bo6b$2b2o2bo2bo7b$bo7bo7b$2o3b2ob2o7b$b5o
3bobo5b$2bo5bo2bo5b$9bobo5b3$12b2o3b$11b2ob3o$12b5o$13b3o!`,
  ),
  parseRle(
    'space_rake',
    `11b2o5b4o$9b2ob2o3bo3bo$9b4o8bo$10b2o5bo2bob2$8bo13b$7b2o8b2o3b$6bo9bo
2bo2b$7b5o4bo2bo2b$8b4o3b2ob2o2b$11bo4b2o4b4$18b4o$o2bo13bo3bo$4bo16bo
$o3bo12bo2bob$b4o!`,
  ),
  parseRle(
    'backrake_1',
    `5b3o11b3o5b$4bo3bo9bo3bo4b$3b2o4bo7bo4b2o3b$2bobob2ob2o5b2ob2obobo2b$b
2obo4bob2ob2obo4bob2ob$o4bo3bo2bobo2bo3bo4bo$12bobo12b$2o7b2obobob2o7b
2o$12bobo12b$6b3o9b3o6b$6bo3bo9bo6b$6bobo4b3o11b$12bo2bo4b2o5b$15bo11b
$11bo3bo11b$11bo3bo11b$15bo11b$12bobo!`,
  ),
  parseRle(
    'backrake_2',
    `3bo15b$2b3o14b$b2obo5bo8b$b3o5b3o7b$2b2o4bo2b2o3b3o$8b3o4bo2bo$18bo$
18bo$18bo$2b3o12bob$2bo2bo13b$2bo16b$2bo16b$3bo15b7$3o16b$o2bo11bo3b$o
13b3o2b$o12b2obo2b$o12b3o3b$bo12b2o`,
  ),
];
