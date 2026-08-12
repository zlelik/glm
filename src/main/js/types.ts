// API DTO types - mirror the backend DTOs (info.gamed.glm.dto).

export interface PlayerDto {
  id: number;
  nickName: string;
}

export interface GamePlayerDto {
  id: number;
  nickName: string;
  color: string;
}

// A cell: grid coordinates plus its owner's player id. Used for both placing cells (create/join) and
// rendering. On input the server validates the playerId is the current player and sets the owner itself.
export interface CellDto {
  x: number;
  y: number;
  playerId: number;
}

export interface GameDetailsDto {
  id: number;
  width: number;
  height: number;
  player1: GamePlayerDto | null;
  player2: GamePlayerDto | null;
  cells: CellDto[];
  // Each player's remaining "accumulated cell" credits (each iteration grants +1; placing a cell spends 1).
  player1AccumulatedCells: number;
  player2AccumulatedCells: number;
}

// The current player's active game id (null when they have none).
export interface GameHubDto {
  gameId: number | null;
}

// Outcome of a game (GET /api/games/{id}/result). winnerId: a player id, -1 for a draw, or null if it
// ended without a winner (a player exited).
export interface GameResultDto {
  status: string;
  winnerId: number | null;
}

// A selectable board size with a human label for the dropdown.
export interface GameSize {
  width: number;
  height: number;
  label: string;
}

// A selectable colour: the hex value plus a stable i18n key (translated as color_<key>).
export interface ColorOption {
  value: string;
  key: string;
}

// Options for the create/join forms (GET /api/games/options).
export interface GameOptions {
  colors: ColorOption[];
  defaultColor: string;
  sizes: GameSize[];
  defaultSize: GameSize;
}

// A game waiting for a second player, as shown in the "search existing game" list.
export interface JoinableGame {
  gameId: number;
  player1: GamePlayerDto;
  width: number;
  height: number;
}

// One row of the profile's match history. Fields are from the current player's perspective:
// result is WON/LOST/DRAW for a finished game or ONGOING while active; finishedAt is null while ongoing.
export interface GameSummaryDto {
  gameId: number;
  opponentNickName: string | null;
  width: number;
  height: number;
  status: string;
  result: string;
  finishedAt: string | null;
}

// The current player's profile (GET /api/games/profile): stats summary + match history (newest first).
export interface ProfileDto {
  nickName: string;
  gamesPlayed: number;
  gamesWon: number;
  gamesLost: number;
  gamesDrawn: number;
  games: GameSummaryDto[];
}
