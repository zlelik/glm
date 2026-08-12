package info.gamed.glm.dto;

/**
 * A cell: its grid coordinates and the id of the player that owns it. One type for both directions - on
 * output the owner is the real owner; on input the client states its own player id, which the server
 * VALIDATES (a cell may only be claimed by the current player) and then sets the owner from the session
 * regardless (the request's playerId is never trusted for ownership). See GameService.sanitizeCells.
 */
public record CellDto(int x, int y, Long playerId) {
}
