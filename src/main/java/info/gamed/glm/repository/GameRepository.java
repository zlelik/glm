package info.gamed.glm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import info.gamed.glm.entity.Game;

/**
 * Standard Spring Data JPA repository for {@link Game}. Games are read via GameController, not exposed
 * as a REST resource.
 * @author Z@
 */
@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    // Custom queries can be added here if needed
    
    // Loads game and players in 1 SQL query because players are defined with eager loading, but does not load cells information.
    Optional<Game> findById(Long id);
    
    // Loads game, players, and cells in a single query
    @EntityGraph(attributePaths = {"player1", "player2", "cells"})
    Optional<Game> findDetailedInfoById(Long id);
    
    // Fetch only the game IDs
    @Query("SELECT g.id FROM Game g")
    List<Long> findAllIds();

    // Ids of games ready to run: status ACTIVE (both players joined, not finished). A WAITING game stays
    // frozen until someone joins; a FINISHED game is never processed.
    @Query("SELECT g.id FROM Game g WHERE g.status = info.gamed.glm.entity.GameStatus.ACTIVE")
    List<Long> findActiveGameIds();

    // The player's current (not-yet-finished) game, as player1 or player2. Finished games are excluded so
    // the player can start a new one.
    @Query("SELECT g FROM Game g WHERE (g.player1.id = :playerId OR g.player2.id = :playerId) "
            + "AND g.status <> info.gamed.glm.entity.GameStatus.FINISHED")
    List<Game> findByPlayerId(@Param("playerId") Long playerId);

    // Games waiting for a second player, excluding the player's own games (which they cannot join).
    @Query("SELECT g FROM Game g WHERE g.status = info.gamed.glm.entity.GameStatus.WAITING AND g.player1.id <> :playerId")
    List<Game> findJoinableGames(@Param("playerId") Long playerId);

    // The player's match history (for the profile page): every game they played that actually started - i.e.
    // has a second player - as player1 or player2. Ordered by finish date descending (the column shown in the
    // table), most recent first; ongoing games (no finish date yet) sort last via NULLS LAST, with id as a
    // stable tie-breaker for deterministic pagination. Players are eager-loaded, so their nicks come along
    // without extra queries.
    @Query("SELECT g FROM Game g WHERE (g.player1.id = :playerId OR g.player2.id = :playerId) "
            + "AND g.player2 IS NOT NULL ORDER BY g.finishDateTime DESC NULLS LAST, g.id DESC")
    List<Game> findMatchesByPlayerId(@Param("playerId") Long playerId);

    // Aggregate profile stats, counted in the database so they stay correct once the match list above is
    // paginated (counting the returned page would be wrong). "Played" = the player's finished games that had
    // an opponent; "won"/"drawn" are derived from the winner id (a draw is Game.WINNER_DRAW = -1). "Lost" is
    // played - won - drawn, so it needs no separate query.
    @Query("SELECT COUNT(g) FROM Game g WHERE (g.player1.id = :playerId OR g.player2.id = :playerId) "
            + "AND g.player2 IS NOT NULL AND g.status = info.gamed.glm.entity.GameStatus.FINISHED")
    long countFinishedByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT COUNT(g) FROM Game g WHERE g.status = info.gamed.glm.entity.GameStatus.FINISHED "
            + "AND g.winnerId = :playerId")
    long countWonByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT COUNT(g) FROM Game g WHERE g.status = info.gamed.glm.entity.GameStatus.FINISHED "
            + "AND g.winnerId = -1 AND (g.player1.id = :playerId OR g.player2.id = :playerId)")
    long countDrawnByPlayerId(@Param("playerId") Long playerId);

    // Used by DatabaseLoader to look up a seeded game by its name when seeding dependent data.
    Game findByGameName(String gameName);
}
