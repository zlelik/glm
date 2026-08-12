package info.gamed.glm.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import info.gamed.glm.entity.Cell;

/**
 * Standard Spring Data JPA repository for {@link Cell}. Cells are managed server-side and read via
 * GameController, not exposed as a REST resource.
 * @author Z@
 */
@Repository
public interface CellRepository extends JpaRepository<Cell, Long> {
    // Custom queries can be added here if needed
    
    /**
     * This method does bulk delete because standard bulk delete does not work somehow.
     * @param ids
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Cell c WHERE c.id IN :ids")
    void deleteAllCellsById(@Param("ids") List<Long> ids);

    /** Deletes every cell of a game (used when a game ends - the game row stays, its cells go). */
    @Transactional
    @Modifying
    @Query("DELETE FROM Cell c WHERE c.game.id = :gameId")
    void deleteByGameId(@Param("gameId") Long gameId);

    /** Whether a live cell already occupies the given position in a game. */
    @Query("SELECT COUNT(c) > 0 FROM Cell c WHERE c.game.id = :gameId AND c.xPosition = :x AND c.yPosition = :y")
    boolean existsCellAt(@Param("gameId") Long gameId, @Param("x") int x, @Param("y") int y);

    /**
     * Deletes the cells of every game that finished before the given cutoff (the game rows are kept, with
     * their timestamps, for stats). One set-based statement; returns the number of cells removed.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Cell c WHERE c.game.id IN "
            + "(SELECT g.id FROM Game g WHERE g.status = info.gamed.glm.entity.GameStatus.FINISHED AND g.finishDateTime < :cutoff)")
    int deleteCellsOfGamesFinishedBefore(@Param("cutoff") Instant cutoff);
}
