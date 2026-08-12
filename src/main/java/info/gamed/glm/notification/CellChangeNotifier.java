package info.gamed.glm.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import info.gamed.glm.config.WebSocketConfiguration;

/**
 * Pushes a WebSocket message to subscribed clients when a game's cells change, so the client reloads
 * the game. The game logic calls these methods directly.
 *
 * This replaces the former Spring Data REST {@code @RepositoryEventHandler}: instead of publishing
 * Spring Data REST AfterCreate/AfterDelete events and handling them, the change is signalled directly.
 * @author Z@
 */
@Component
public class CellChangeNotifier {

    private final SimpMessagingTemplate websocket;

    public CellChangeNotifier(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }

    /** Signal that a cell was created. The id is sent as the payload; clients use the message only as a reload trigger. */
    public void cellCreated(Long cellId) {
        websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/newCell", cellId);
    }

    /** Signal that a cell was deleted. */
    public void cellDeleted(Long cellId) {
        websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/deleteCell", cellId);
    }
}
