package info.gamed.glm.scheduledtask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import info.gamed.glm.entity.Cell;
import info.gamed.glm.entity.Game;
import info.gamed.glm.entity.GameStatus;
import info.gamed.glm.entity.Player;
import info.gamed.glm.repository.CellRepository;
import info.gamed.glm.repository.GameRepository;
import info.gamed.glm.repository.PlayerRepository;

/**
 * Verifies the daily cleanup deletes the cells of games finished more than 24h ago, and keeps recently
 * finished (and ongoing) games' cells. Builds the games directly via the repositories (the seeded players
 * exist) so the cleanup can be exercised without playing a full game.
 */
@SpringBootTest
class FinishedGameCleanupTest {

    @Autowired
    private FinishedGameCleanup cleanup;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private CellRepository cellRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void purgesCellsOnlyForGamesFinishedOver24hAgo() {
        Player player1 = playerRepository.findByLoginName("player1");
        Player player2 = playerRepository.findByLoginName("player2");

        Long oldCellId = saveFinishedGameWithCell(player1, player2, 25);   // finished 25h ago -> purge
        Long recentCellId = saveFinishedGameWithCell(player1, player2, 1);  // finished 1h ago  -> keep

        cleanup.purgeOldFinishedGameCells();

        assertFalse(cellRepository.existsById(oldCellId), "cells of a game finished >24h ago should be purged");
        assertTrue(cellRepository.existsById(recentCellId), "cells of a recently finished game should be kept");
    }

    private Long saveFinishedGameWithCell(Player player1, Player player2, long finishedHoursAgo) {
        Game game = new Game("cleanup-test", 20, 20, "#008800", "#0000CC", player1, player2, "system",
                Game.BOUNDARY_CONDITION_PERIODIC);
        game.setCreationDateTime(Instant.now().minus(finishedHoursAgo + 1, ChronoUnit.HOURS));
        game.setStatus(GameStatus.FINISHED);
        game.setFinishDateTime(Instant.now().minus(finishedHoursAgo, ChronoUnit.HOURS));
        gameRepository.save(game);
        return cellRepository.save(new Cell(1, 1, game, player1)).getId();
    }
}
