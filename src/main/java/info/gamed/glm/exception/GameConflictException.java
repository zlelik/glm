package info.gamed.glm.exception;

/**
 * Thrown when a game action conflicts with the current state - e.g. creating/joining while you already
 * have a game, joining a game that is already full, or joining your own game. Mapped to HTTP 409 by
 * {@link GlobalExceptionHandler}.
 * @author Z@
 */
public class GameConflictException extends RuntimeException {

    public GameConflictException(String message) {
        super(message);
    }
}
