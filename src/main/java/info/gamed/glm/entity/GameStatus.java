package info.gamed.glm.entity;

/**
 * Lifecycle status of a game. Stored as a string (see Game.status). A game is never deleted; it ends up
 * FINISHED.
 * @author Z@
 */
public enum GameStatus {
    /** Created, waiting for a second player to join. */
    WAITING,
    /** Both players present; processed by the game loop. */
    ACTIVE,
    /** Ended (a player exited, or the game was won/drawn). Cells are deleted; the row is kept. */
    FINISHED
}
