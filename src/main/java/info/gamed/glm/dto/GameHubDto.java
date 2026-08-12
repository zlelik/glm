package info.gamed.glm.dto;

/**
 * Result of GET /api/games/my (and the create/join responses): the id of the player's active game, or
 * null if they have none (the client then offers to create or join a game).
 */
public record GameHubDto(Long gameId) {
}
