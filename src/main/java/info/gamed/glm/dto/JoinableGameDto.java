package info.gamed.glm.dto;

/**
 * A game that is waiting for a second player, as shown in the "search existing game" list: the game id,
 * player1 (reusing GamePlayerDto: id, nick name, colour) and the board size.
 */
public record JoinableGameDto(Long gameId, GamePlayerDto player1, int width, int height) {
}
