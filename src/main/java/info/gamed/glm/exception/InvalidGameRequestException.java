package info.gamed.glm.exception;

/**
 * Thrown when a create/join request is malformed or breaks a game rule - e.g. an unknown colour, an
 * unsupported board size, or a cell that is out of bounds or on the wrong half of the board. Mapped to
 * HTTP 400 by {@link GlobalExceptionHandler}.
 * @author Z@
 */
public class InvalidGameRequestException extends RuntimeException {

    public InvalidGameRequestException(String message) {
        super(message);
    }
}
