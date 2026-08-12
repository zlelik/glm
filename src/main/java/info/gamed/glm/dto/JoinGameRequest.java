package info.gamed.glm.dto;

import java.util.List;

/**
 * Request body for POST /api/games/{id}/join: the joining player's chosen colour and the cells they
 * placed on their (right) half of the board. The board size comes from the existing game.
 */
public record JoinGameRequest(String color, List<CellDto> cells) {
}
