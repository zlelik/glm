package info.gamed.glm.dto;

/**
 * A player as seen inside a specific game: their identity plus the colour assigned to them in that
 * game (the colour is a per-game property, so it is grouped with the player here).
 */
public record GamePlayerDto(Long id, String nickName, String color) {
}
