package info.gamed.glm.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central REST exception handler. Translates exceptions thrown by controllers into RFC 7807
 * {@link ProblemDetail} responses (Content-Type: application/problem+json), so the API returns a
 * consistent, typed error body and the correct HTTP status instead of a raw stack trace.
 * @author Z@
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Any missing resource -> 404 Not Found. Mapped on the ResourceNotFoundException abstraction, so new
     * not-found exceptions only need to extend it - this handler stays closed for modification (OCP). The
     * detail message comes from the actual subclass (e.g. GameNotFoundException), so it stays specific.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    /** Action conflicts with the current game state (already in a game, game full, joining own game) -> 409. */
    @ExceptionHandler(GameConflictException.class)
    public ProblemDetail handleGameConflict(GameConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Game conflict");
        return problem;
    }

    /** Malformed/invalid create or join request (bad colour, size, or cell placement) -> 400. */
    @ExceptionHandler(InvalidGameRequestException.class)
    public ProblemDetail handleInvalidGameRequest(InvalidGameRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid game request");
        return problem;
    }

    /** Authenticated principal has no matching player (e.g. a stale session) -> 401. */
    @ExceptionHandler(UnauthenticatedException.class)
    public ProblemDetail handleUnauthenticated(UnauthenticatedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthenticated");
        return problem;
    }

    /**
     * Missing static resource -> a quiet 404 (NOT a logged 500 via the catch-all below). This is hit by,
     * e.g., browsers probing /.well-known/... and any unknown path; it is a normal not-found, not an error.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
    }

    /**
     * Last-resort handler for anything not mapped above. The full cause is logged server-side, but the
     * client gets a generic message so internal details / stack traces are never leaked.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error handling request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setTitle("Internal server error");
        return problem;
    }
}
