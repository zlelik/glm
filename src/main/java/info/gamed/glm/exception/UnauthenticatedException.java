package info.gamed.glm.exception;

/**
 * Thrown when a request is processed for a principal that has no matching {@link info.gamed.glm.entity.Player}
 * (e.g. a stale session referencing a user that no longer exists). Mapped to HTTP 401 by
 * {@link GlobalExceptionHandler} so the client re-authenticates, instead of failing with a 500/NPE.
 * @author Z@
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
