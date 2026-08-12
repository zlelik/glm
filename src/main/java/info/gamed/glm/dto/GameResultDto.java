package info.gamed.glm.dto;

import info.gamed.glm.entity.GameStatus;

/**
 * The outcome of a game (GET /api/games/{id}/result): its status and, once FINISHED, the winner - a
 * player id, {@code Game.WINNER_DRAW} (-1) for a draw, or null if it ended without a winner (a player
 * exited). The client uses it to show a "You win / You lose / Draw" message.
 */
public record GameResultDto(GameStatus status, Long winnerId) {
}
