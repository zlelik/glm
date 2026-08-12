package info.gamed.glm.gamelogic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import info.gamed.glm.config.GameProperties;
import info.gamed.glm.entity.Cell;
import info.gamed.glm.entity.Game;
import info.gamed.glm.entity.GameStatus;
import info.gamed.glm.entity.Player;
import info.gamed.glm.exception.GameNotFoundException;
import info.gamed.glm.notification.CellChangeNotifier;
import info.gamed.glm.repository.CellRepository;
import info.gamed.glm.repository.GameRepository;
import info.gamed.glm.service.CellService;

/**
 * Processes a single game: calculates its next generation and persists the resulting cell changes.
 * @author Z@
 */
@Component
public class SingleGameProcessor {
    private static final Logger log = LoggerFactory.getLogger(SingleGameProcessor.class);
    
    private final GameRepository gameRepository;
    private final CellRepository cellRepository;
    private final CellService cellService;
    private final CellChangeNotifier cellChangeNotifier;
    private final GameProperties gameProperties;

    public SingleGameProcessor(GameRepository gameRepository, CellRepository cellRepository, CellService cellService,
                               CellChangeNotifier cellChangeNotifier, GameProperties gameProperties) {
        this.gameRepository = gameRepository;
        this.cellRepository = cellRepository;
        this.cellService = cellService;
        this.cellChangeNotifier = cellChangeNotifier;
        this.gameProperties = gameProperties;
    }

    /**
     * Processes a single game: loads it, calculates the next generation, and either persists the cell
     * changes (deletes/creates) + publishes the websocket events, or - if the automatic game-over rules
     * trigger - finishes the game (cells deleted, winner recorded). Runs in its own transaction.
     * @param id the game id
     * @return true if this call finished the game (the caller then notifies clients post-commit)
     */
    // NOTE: intentionally NOT @Async. Invoked one game per task from GameLogicProcessor's bounded pool;
    // @Transactional gives each game its own transaction. The bounded pool plus the per-run completion
    // barrier ensure a game is never processed by two threads at once (which corrupted cells).
    @Transactional
    public boolean processSingleGame(Long id) {
        Long startTimeSingle = System.currentTimeMillis();
        log.debug("processSingleGameData started");
        Game game = gameRepository.findDetailedInfoById(id).orElseThrow(() -> new GameNotFoundException(id));

        // New iteration: grant each player one "accumulated cell" credit (capped). Players spend these to
        // place single cells or whole known objects during the game; banking several ticks lets them drop a
        // multi-cell object. Any cell added during the previous iteration is already in the loaded cells.
        int accumulatedCap = gameProperties.getMaxAccumulatedCells();
        game.setPlayer1AccumulatedCells(grantCredit(game.getPlayer1AccumulatedCells(), accumulatedCap));
        game.setPlayer2AccumulatedCells(grantCredit(game.getPlayer2AccumulatedCells(), accumulatedCap));

        log.debug(String.format("processSingleGameData fetched game id [%s]", game.getId()));
        log.debug(String.format("processSingleGameData try to generate new game state for game id [%s]", game.getId()));
        List<Cell> cells = game.getCells();
        log.debug(String.format("processSingleGameData fetched cells. cells.size(): %s", cells.size()));
        List<Cell> nextGenerationCells = getNextGameState(game, cells);
        log.debug(String.format("processSingleGameData new game state has been generated for game id [%s]", game.getId()));
        log.debug(String.format("processSingleGameData new game state size. nextGenerationCells.size(): %s", nextGenerationCells.size()));
        
        log.debug(String.format("processSingleGameData remove cells from cells list which are in the nextGenerationCells list and remove cells from nextGenerationCells which are in cells list to avoid delete/create only changed cells and for game id [%s]", game.getId()));


        // Diff current vs next by (position, owner) in O(n): a cell is "unchanged" only if the SAME owner
        // occupies the SAME position in both generations. Index each side's keys in a hash set, then a cell
        // is deleted if its key is absent from the next generation, and created if its key is new. (The
        // previous implementation compared the two lists with nested scans - O(n^2) - which dominated ticks
        // on big boards.)
        Set<String> currentKeys = new HashSet<>(cells.size() * 2);
        for (Cell cell : cells) {
            currentKeys.add(cellKey(cell));
        }
        Set<String> nextKeys = new HashSet<>(nextGenerationCells.size() * 2);
        for (Cell nextCell : nextGenerationCells) {
            nextKeys.add(cellKey(nextCell));
        }

        // Existing cells whose (position, owner) is not carried into the next generation are deleted.
        List<Cell> cellsToDelete = new ArrayList<>();
        for (Cell cell : cells) {
            if (!nextKeys.contains(cellKey(cell))) {
                cellsToDelete.add(cell);
            }
        }
        // Next-generation cells that did not already exist (same position + owner) are created.
        List<Cell> cellsToCreate = new ArrayList<>();
        for (Cell nextCell : nextGenerationCells) {
            if (!currentKeys.contains(cellKey(nextCell))) {
                cellsToCreate.add(nextCell);
            }
        }
        
        log.debug(String.format("processSingleGameData cellsToDelete. cellsToDelete.size(): %s", cellsToDelete.size()));
        log.debug(String.format("processSingleGameData cellsToCreate. cellsToCreate.size(): %s", cellsToCreate.size()));

        // Evaluate the automatic game-over rules on the would-be next state. We still PERSIST that state
        // below, even when the game ends, so the saved board IS the final generation - that way players see
        // the actual last state under the result popup (the FINISHED game keeps its cells; they are not wiped).
        boolean noChange = cellsToDelete.isEmpty() && cellsToCreate.isEmpty();
        boolean gameOver = evaluateGameOver(game, nextGenerationCells, noChange);

        log.debug(String.format("processSingleGameData delete all old cells for game id [%s]", game.getId()));
        //delete all old cells
        if (cellsToDelete.size() > 0) {
            log.debug("processSingleGameData delete all cells");
            //this.cellRepository.deleteAll(cells);
            this.cellService.deleteAllCells(cellsToDelete);//this is custom batch delete.
            log.debug("processSingleGameData notify clients after delete");
            cellChangeNotifier.cellDeleted(cellsToDelete.get(0).getId());
        }

        log.debug(String.format("processSingleGameData save/create all new generation cells for game id [%s]", game.getId()));
        if (cellsToCreate.size() > 0) {
            log.debug("processSingleGameData create all cells");
            // Inserts ARE JDBC-batched (verified: 100 new cells -> two batches of 50, honouring
            // hibernate.jdbc.batch_size=50). This works because Cell's @GeneratedValue is AUTO, which
            // Hibernate 7 maps to a pooled SequenceStyleGenerator (a real sequence on H2, a table-backed
            // one on MySQL). Ids are allocated in memory a block at a time, so the actual INSERTs are
            // deferred to flush and grouped into batches. (An IDENTITY / AUTO_INCREMENT id would instead
            // force one immediate INSERT per row to read back the generated key, silently disabling
            // batching - the classic trap this configuration avoids.)
            this.cellRepository.saveAll(cellsToCreate);
            log.debug("processSingleGameData notify clients after save");
            cellChangeNotifier.cellCreated(cellsToCreate.get(0).getId());
        }
        Long endTimeSingle = System.currentTimeMillis();
        log.debug(String.format("processSingleGameData finished. Total time: [%ss]", (endTimeSingle - startTimeSingle) / 1000.0));
        return gameOver;
    }

    /**
     * Applies the automatic game-over rules to the would-be next state. If the game is over it marks it
     * FINISHED and records the winner (or {@link Game#WINNER_DRAW}), then returns true:
     *  - a player has no live cells left  -> the other player wins (both empty -> draw);
     *  - the board is unchanged for more than gml.max-stale-iterations -> more live cells wins (equal ->
     *    draw). A non-positive max disables this rule.
     * Otherwise it updates the stale-iteration counter and returns false. It does NOT delete cells: the
     * caller persists this generation so the FINISHED game keeps its final board for the result view.
     */
    private boolean evaluateGameOver(Game game, List<Cell> nextGenerationCells, boolean noChange) {
        long player1Count = countCellsOf(nextGenerationCells, game.getPlayer1());
        long player2Count = countCellsOf(nextGenerationCells, game.getPlayer2());

        Long winnerId = null;
        boolean gameOver = false;
        if (player1Count == 0 || player2Count == 0) {
            gameOver = true;
            if (player1Count == 0 && player2Count == 0) {
                winnerId = Game.WINNER_DRAW;
            } else {
                winnerId = (player1Count > 0) ? game.getPlayer1().getId() : game.getPlayer2().getId();
            }
        } else if (noChange) {
            int stale = game.getStaleIterations() + 1;
            game.setStaleIterations(stale);
            int maxStale = gameProperties.getMaxStaleIterations();
            if (maxStale > 0 && stale > maxStale) {
                gameOver = true;
                winnerId = (player1Count > player2Count) ? game.getPlayer1().getId()
                        : (player2Count > player1Count) ? game.getPlayer2().getId() : Game.WINNER_DRAW;
            }
        } else {
            game.setStaleIterations(0);
        }

        if (!gameOver) {
            return false;
        }
        game.setStatus(GameStatus.FINISHED);
        game.setWinnerId(winnerId);
        game.setFinishDateTime(Instant.now());
        log.info(String.format("Game %s finished automatically. winnerId: %s (player1 cells: %s, player2 cells: %s)",
                game.getId(), winnerId, player1Count, player2Count));
        return true;
    }

    /** One iteration's credit for a player: current + 1, clamped to the cap (a non-positive cap = unbounded). */
    private int grantCredit(int current, int cap) {
        int next = current + 1;
        return (cap > 0 && next > cap) ? cap : next;
    }

    private long countCellsOf(List<Cell> cells, Player player) {
        if (player == null) {
            return 0;
        }
        return cells.stream()
                .filter(c -> c.getPlayer() != null && c.getPlayer().getId().equals(player.getId()))
                .count();
    }
    
    
    /**
     * This function calculates next generation of the cells according to these rules: 
     * 1. пустая (мёртвая) клетка, рядом с которой ровно три живые клетки, оживает.
     * 1. If empty (died) cell has exactly 3 alive neighbours cells around, then it becomes alive. 
     * 2. если у живой клетки есть две или три живые соседки, то эта клетка продолжает жить.
     * 2. If alive cell has 2 (==2) or 3 (==3) neighbours, then it continue to be alive (keep it's state alive).
     * 3. если соседей меньше двух или больше трёх, то клетка умирает (от «одиночества» или от «перенаселённости»).
     * 3. If alive cell has less than 2 (<2) or more than 3 (>3) neighbours, then it dies from "loneliness" or "overcrowding".
     * 4a. Если у мёртвой клетки, которая должна ожить своих соседей меньше чем чужих, то она умирает (ее съедают враги).
     * 4a. If empty (died) cell, which should become alive according to rule 1, number of it's own neighbours less then number of alien neighbours, then it dies (aliens eat it).  
     * 4b. Если у живой клетки по правилу 2 чужих соседей больше или равно 2, то она умирает (ее съедают враги).
     * 4b. If alive cell according to rule 2 number of alien neighbours more or equal 2, then it dies (aliens eat it).
     * 
     * @param gameXDimension
     * @param gameYDimension
     * @param cells
     * @return
     */
    // Package-private (not private) so it can be unit-tested directly; it is otherwise an internal step.
    //
    // Performance: this is the per-tick hot path. It is O(n) in the number of live cells n (independent of the
    // board area): live cells are indexed in a hash map so each of a candidate's 8 neighbour look-ups is O(1),
    // and the candidate set / final de-duplication use hash sets. The previous implementation was O(n^2) - for
    // every candidate it scanned the whole cell list for each neighbour, and de-duplicated with nested list
    // scans - which made big, busy boards lag. The game rules (1, 2, 3, 4a, 4b) and the three boundary modes
    // are preserved exactly; only the data structures changed.
    List<Cell> getNextGameState(Game game, List<Cell> cells) {
        long startTime = System.currentTimeMillis();
        log.debug("getNextGameState started. game_id: {}", game.getId());

        int gameXDimension = game.getGameXDimension();
        int gameYDimension = game.getGameYDimension();
        int bcMode = game.getBcMode();

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();
        Long player1Id = player1.getId();
        Long player2Id = player2.getId();

        // Periodic and stop-on-edge both count neighbours across the wrapped edge (unlike go-to-infinity).
        boolean wrapNeighbours = (bcMode == Game.BOUNDARY_CONDITION_PERIODIC)
                || (bcMode == Game.BOUNDARY_CONDITION_STOP_ON_EDGE);

        // Index live cells by packed (x,y) position for O(1) neighbour look-ups. Positions are unique
        // (create/join/addCells all de-duplicate), so each position has a single owner.
        Map<Long, Long> ownerByPosition = new HashMap<>(cells.size() * 2);
        for (Cell c : cells) {
            ownerByPosition.putIfAbsent(pack(c.getXPosition(), c.getYPosition()), c.getPlayer().getId());
        }

        // Candidate positions to evaluate = every live cell plus its 8 neighbours (raw positions), de-duplicated.
        // Same candidate set as before, built via a hash set (O(n)) instead of nested list scans (O(n^2)).
        LinkedHashSet<Long> candidatePositions = new LinkedHashSet<>(Math.max(16, cells.size() * 8));
        for (Cell c : cells) {
            int x = c.getXPosition();
            int y = c.getYPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    candidatePositions.add(pack(x + dx, y + dy));
                }
            }
        }

        List<Cell> nextGeneration = new ArrayList<>();
        for (long position : candidatePositions) {
            int x = unpackX(position);
            int y = unpackY(position);

            // stop-on-edge: cells outside the board neither survive nor are born (they are simply dropped).
            if (bcMode == Game.BOUNDARY_CONDITION_STOP_ON_EDGE
                    && !(x >= 0 && x < gameXDimension && y >= 0 && y < gameYDimension)) {
                continue;
            }

            int player1NeighboursCount = 0;
            int player2NeighboursCount = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    Long neighbourOwner = ownerByPosition.get(pack(x + dx, y + dy));
                    if (neighbourOwner == null && wrapNeighbours) {
                        // At an edge the neighbour position wraps around to the opposite side.
                        neighbourOwner = ownerByPosition.get(pack(
                                getPositionOverEdge(x + dx, gameXDimension),
                                getPositionOverEdge(y + dy, gameYDimension)));
                    }
                    if (neighbourOwner != null) {
                        if (neighbourOwner.equals(player1Id)) {
                            player1NeighboursCount++;
                        } else if (neighbourOwner.equals(player2Id)) {
                            player2NeighboursCount++;
                        }
                    }
                }
            }

            Long ownerHere = ownerByPosition.get(position);
            Player nextOwner = null;
            if (ownerHere == null) {
                // Dead cell - rule 1 (born with exactly 3 neighbours) + rule 4a (born for whichever player
                // strictly out-numbers the other; a tie leaves it dead, i.e. it is "eaten").
                if ((player1NeighboursCount < player2NeighboursCount) && (player2NeighboursCount == 3)) {
                    nextOwner = player2;
                } else if ((player2NeighboursCount < player1NeighboursCount) && (player1NeighboursCount == 3)) {
                    nextOwner = player1;
                }
            } else {
                // Live cell - rule 2 (survives with 2 or 3 of its own neighbours) + rule 4b (dies when the
                // alien neighbours out-number its own). Conditions mirror the original exactly.
                if ((player1NeighboursCount <= player2NeighboursCount)
                        && (player2NeighboursCount == 2 || player2NeighboursCount == 3)
                        && ownerHere.equals(player2Id)) {
                    nextOwner = player2;
                } else if ((player2NeighboursCount <= player1NeighboursCount)
                        && (player1NeighboursCount == 2 || player1NeighboursCount == 3)
                        && ownerHere.equals(player1Id)) {
                    nextOwner = player1;
                }
            }

            if (nextOwner != null) {
                nextGeneration.add(new Cell(x, y, game, nextOwner));
            }
        }

        if ((bcMode == Game.BOUNDARY_CONDITION_PERIODIC) || (bcMode == Game.BOUNDARY_CONDITION_STOP_ON_EDGE)) {
            nextGeneration.forEach(cell -> {
                cell.setXPosition(getPositionOverEdge(cell.getXPosition(), gameXDimension));
                cell.setYPosition(getPositionOverEdge(cell.getYPosition(), gameYDimension));
            });
        } else if (bcMode == Game.BOUNDARY_CONDITION_GO_TO_INFINITY) {
            // Once every one of a player's cells has drifted out of the visible field, drop them all (the
            // object has "gone to infinity"). Same rule as before.
            for (Long playerId : Arrays.asList(player1Id, player2Id)) {
                boolean allCellsOutsideGameField = nextGeneration.stream()
                        .filter(cell -> Objects.equals(cell.getPlayer().getId(), playerId))
                        .allMatch(cell -> (
                                cell.getXPosition() < -10
                                || cell.getYPosition() < -10
                                || cell.getXPosition() > (gameXDimension - 10)
                                || cell.getYPosition() > (gameYDimension - 10)));
                if (allCellsOutsideGameField) {
                    nextGeneration.removeIf(cell -> Objects.equals(cell.getPlayer().getId(), playerId));
                }
            }
        }

        // De-duplicate by (x, y, owner) - positions can coincide after the edge wrap above. O(n) via a set.
        Set<String> seenPositionsFinal = new HashSet<>(nextGeneration.size() * 2);
        nextGeneration.removeIf(cell ->
                !seenPositionsFinal.add(cell.getXPosition() + "_" + cell.getYPosition() + "_" + cell.getPlayer().getId()));

        if (log.isDebugEnabled()) {
            log.debug("getNextGameState finished. game_id: {}. cells: {}. Total time: [{}s]",
                    game.getId(), nextGeneration.size(), (System.currentTimeMillis() - startTime) / 1000.0);
        }
        return nextGeneration;
    }

    /** Diff key identifying a cell by its position AND owner (a cell is "unchanged" only if both match). */
    private static String cellKey(Cell cell) {
        Player owner = cell.getPlayer();
        return cell.getXPosition() + "_" + cell.getYPosition() + "_" + (owner == null ? "?" : owner.getId());
    }

    /** Packs a signed (x, y) grid position into a single long key (x in the high 32 bits, y in the low 32). */
    private static long pack(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackY(long packed) {
        return (int) packed;
    }
    
    // Package-private for unit testing of the periodic/stop-on-edge wrap logic.
    int getPositionOverEdge(int position, int dimension) {
        if (position >= dimension) {
            return position - dimension;
        } else if (position < 0) {
            return dimension + position;
        } else {
            return position;
        }
    }
    
}