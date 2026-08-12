package info.gamed.glm.exception;

/**
 * Thrown when a game cannot be found for a given id. Extends {@link ResourceNotFoundException}, so it is
 * mapped to HTTP 404 by {@link GlobalExceptionHandler} without the handler needing a game-specific method.
 * @author Z@
 */
public class GameNotFoundException extends ResourceNotFoundException {

    public GameNotFoundException(Long id) {
        super(String.format("Game for id: [%s] not found", id));
    }
}
