package info.gamed.glm.gamelogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import info.gamed.glm.entity.Cell;
import info.gamed.glm.entity.Game;
import info.gamed.glm.entity.Player;

/**
 * Unit tests for the Game-of-Life logic. getNextGameState/getPositionOverEdge use none of the injected
 * dependencies, so the processor is built with nulls. Using cells from a single player makes the custom
 * two-player rules reduce to classic Conway, so well-known patterns (blinker, block) give known results.
 */
class SingleGameProcessorTest {

    private final SingleGameProcessor processor = new SingleGameProcessor(null, null, null, null, null);

    private final Player player1 = playerMock(1L);
    private final Player player2 = playerMock(2L);

    private static Player playerMock(long id) {
        Player p = mock(Player.class);
        when(p.getId()).thenReturn(id);
        return p;
    }

    private Game game(int width, int height, int bcMode) {
        Game game = new Game("test", width, height, "#FF0000", "#00FF00", player1, player2, "mgr", bcMode);
        // The id is normally DB-generated; set it here (test only) so logs read game_id: 1 instead of
        // null. ReflectionTestUtils keeps the entity clean (no production setId). Assertions don't use it.
        ReflectionTestUtils.setField(game, "id", 1L);
        return game;
    }

    private static Set<String> positions(List<Cell> cells) {
        return cells.stream().map(c -> c.getXPosition() + "_" + c.getYPosition()).collect(Collectors.toSet());
    }

    /** Owner id of the cell at (x, y) in the given generation, or null if that position is empty. */
    private static Long ownerAt(List<Cell> cells, int x, int y) {
        return cells.stream()
                .filter(c -> c.getXPosition() == x && c.getYPosition() == y)
                .map(c -> c.getPlayer().getId())
                .findFirst().orElse(null);
    }

    // ---- getPositionOverEdge (periodic / stop-on-edge wrap) ----

    @Test
    void positionInsideBoardIsUnchanged() {
        assertEquals(0, processor.getPositionOverEdge(0, 10));
        assertEquals(5, processor.getPositionOverEdge(5, 10));
        assertEquals(9, processor.getPositionOverEdge(9, 10));
    }

    @Test
    void positionPastRightEdgeWrapsToStart() {
        assertEquals(0, processor.getPositionOverEdge(10, 10));
        assertEquals(2, processor.getPositionOverEdge(12, 10));
    }

    @Test
    void positionBeforeLeftEdgeWrapsToEnd() {
        assertEquals(9, processor.getPositionOverEdge(-1, 10));
        assertEquals(7, processor.getPositionOverEdge(-3, 10));
    }

    // ---- getNextGameState: classic Conway behaviour with a single player ----

    @Test
    void blinkerOscillatesHorizontalToVertical() {
        Game game = game(10, 10, Game.BOUNDARY_CONDITION_STOP_ON_EDGE);
        List<Cell> cells = new ArrayList<>(List.of(
                new Cell(4, 4, game, player1),
                new Cell(5, 4, game, player1),
                new Cell(6, 4, game, player1)));

        List<Cell> next = processor.getNextGameState(game, cells);

        assertEquals(Set.of("5_3", "5_4", "5_5"), positions(next));
        next.forEach(c -> assertEquals(1L, c.getPlayer().getId(), "next-gen cells should belong to player1"));
    }

    @Test
    void blockIsAStableStillLife() {
        Game game = game(10, 10, Game.BOUNDARY_CONDITION_STOP_ON_EDGE);
        List<Cell> cells = new ArrayList<>(List.of(
                new Cell(4, 4, game, player1),
                new Cell(5, 4, game, player1),
                new Cell(4, 5, game, player1),
                new Cell(5, 5, game, player1)));

        List<Cell> next = processor.getNextGameState(game, cells);

        assertEquals(Set.of("4_4", "5_4", "4_5", "5_5"), positions(next));
    }

    // ---- getNextGameState: the custom two-player rules (4a, 4b) ----

    @Test
    void rule4aDeadCellBornForTheOutnumberingPlayer() {
        // (5,5) is dead with 3 player2 neighbours above and only 2 player1 neighbours below: player2
        // out-numbers player1 and has exactly 3, so the cell is born for player2 (rule 1 + 4a).
        Game game = game(10, 10, Game.BOUNDARY_CONDITION_STOP_ON_EDGE);
        List<Cell> cells = new ArrayList<>(List.of(
                new Cell(4, 4, game, player2), new Cell(5, 4, game, player2), new Cell(6, 4, game, player2),
                new Cell(4, 6, game, player1), new Cell(5, 6, game, player1)));

        List<Cell> next = processor.getNextGameState(game, cells);

        assertEquals(2L, ownerAt(next, 5, 5), "(5,5) should be born for player2, who out-numbers player1");
    }

    @Test
    void rule4aDeadCellNotBornWhenAliensTie() {
        // (5,5) is dead with 3 player2 neighbours above and 3 player1 neighbours below. Neither strictly
        // out-numbers the other, so the aliens cancel out and no cell is born (rule 4a: it is "eaten").
        Game game = game(10, 10, Game.BOUNDARY_CONDITION_STOP_ON_EDGE);
        List<Cell> cells = new ArrayList<>(List.of(
                new Cell(4, 4, game, player2), new Cell(5, 4, game, player2), new Cell(6, 4, game, player2),
                new Cell(4, 6, game, player1), new Cell(5, 6, game, player1), new Cell(6, 6, game, player1)));

        List<Cell> next = processor.getNextGameState(game, cells);

        assertNull(ownerAt(next, 5, 5), "(5,5) must not be born when alien neighbours tie");
    }

    @Test
    void rule4bLiveCellDiesWhenAlienNeighboursOutnumberOwn() {
        // A player1 cell at (5,5) has 2 player1 neighbours (would survive by rule 2) but 3 player2 neighbours.
        // Because the aliens out-number its own, it is eaten and dies (rule 4b).
        Game game = game(10, 10, Game.BOUNDARY_CONDITION_STOP_ON_EDGE);
        List<Cell> cells = new ArrayList<>(List.of(
                new Cell(5, 5, game, player1),
                new Cell(4, 5, game, player1), new Cell(6, 5, game, player1),
                new Cell(4, 4, game, player2), new Cell(5, 4, game, player2), new Cell(6, 4, game, player2)));

        List<Cell> next = processor.getNextGameState(game, cells);

        assertFalse(positions(next).contains("5_5"), "(5,5) should be eaten by the player2 majority (rule 4b)");
    }

    // ---- getNextGameState: periodic boundary wraps neighbour counting and positions ----

    @Test
    void periodicBlinkerWrapsAroundTheEdge() {
        // A horizontal blinker on the top row (y=0) oscillates to vertical; with periodic edges its top cell
        // wraps from y=-1 to y=9, exercising the wrap in both neighbour counting and the final position.
        Game game = game(10, 10, Game.BOUNDARY_CONDITION_PERIODIC);
        List<Cell> cells = new ArrayList<>(List.of(
                new Cell(0, 0, game, player1),
                new Cell(1, 0, game, player1),
                new Cell(2, 0, game, player1)));

        List<Cell> next = processor.getNextGameState(game, cells);

        assertEquals(Set.of("1_9", "1_0", "1_1"), positions(next));
        next.forEach(c -> assertEquals(1L, c.getPlayer().getId()));
    }
}
