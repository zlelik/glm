package info.gamed.glm.dto;

import java.util.List;

/**
 * Request body for POST /api/games: the creating player's chosen colour, the board size, and the cells
 * they placed on their (left) half of the board.
 */
public record CreateGameRequest(String color, int width, int height, List<CellDto> cells) {
}
