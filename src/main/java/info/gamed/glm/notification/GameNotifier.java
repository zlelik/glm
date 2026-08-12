package info.gamed.glm.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import info.gamed.glm.config.WebSocketConfiguration;

/**
 * Pushes game-lifecycle events to clients over WebSocket. Currently signals that a waiting game has
 * started (a second player joined), so the creator's "waiting for second player" screen can redirect to
 * the live board. The game id is the payload; the client compares it to its own game.
 * @author Z@
 */
@Component
public class GameNotifier {

    private final SimpMessagingTemplate websocket;

    public GameNotifier(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }

    /** Signal that the given game now has both players and has started. */
    public void gameStarted(Long gameId) {
        websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/gameStarted", gameId);
    }

    /** Signal that the given game has ended (a player exited, or it was won/drawn), so clients leave it. */
    public void gameFinished(Long gameId) {
        websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/gameFinished", gameId);
    }
}
