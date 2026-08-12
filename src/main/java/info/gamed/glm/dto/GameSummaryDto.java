package info.gamed.glm.dto;

import java.time.Instant;

import info.gamed.glm.entity.GameStatus;

/**
 * One row of the current player's match history (see ProfileDto / GET /api/games/profile). Fields are
 * from the requesting player's perspective:
 *  - opponentNickName: the other player's nick (null only in the theoretical case of a missing opponent);
 *  - result: "WON" / "LOST" / "DRAW" for a FINISHED game, or "ONGOING" while it is still ACTIVE;
 *  - finishedAt: when the game ended, or null while it is ongoing.
 */
public record GameSummaryDto(Long gameId, String opponentNickName, int width, int height,
                             GameStatus status, String result, Instant finishedAt) {
}
