package info.gamed.glm.dto;

import java.util.List;

/**
 * The current player's profile (GET /api/games/profile): their nick name, aggregate win/loss stats over
 * completed (FINISHED) games, and the full match history (newest first). Only games that actually started
 * - i.e. had a second player - are counted and listed; a game still waiting for an opponent is not a
 * "played" game. Ongoing (ACTIVE) games appear in the history with result "ONGOING" but are not counted in
 * gamesPlayed/won/lost/drawn.
 *
 * For now the page shows only stats; nick-name editing and other profile fields can be added here later.
 */
public record ProfileDto(String nickName, int gamesPlayed, int gamesWon, int gamesLost, int gamesDrawn,
                         List<GameSummaryDto> games) {
}
