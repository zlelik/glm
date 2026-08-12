package info.gamed.glm.dto;

import java.util.List;

/**
 * Detailed view of a game returned by GET /api/games/{id}/details: board size, the two players (with their
 * colours), the live cells (each carrying its owner's player id) and each player's remaining "accumulated
 * cell" credits. This is the API contract the React client consumes; it is intentionally decoupled from the
 * Game JPA entity.
 */
public record GameDetailsDto(
        Long id,
        int width,
        int height,
        GamePlayerDto player1,
        GamePlayerDto player2,
        List<CellDto> cells,
        int player1AccumulatedCells,
        int player2AccumulatedCells) {
}
