package info.gamed.glm.exception;

/**
 * Base type for "resource does not exist" errors. {@link GlobalExceptionHandler} maps this abstraction to
 * HTTP 404, so any new not-found exception only needs to extend this class - the handler stays closed for
 * modification (Open/Closed Principle). Deliberately HTTP-agnostic: subclasses live in the domain and know
 * nothing about the web layer; the status is decided in the handler.
 * @author Z@
 */
public abstract class ResourceNotFoundException extends RuntimeException {

    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
