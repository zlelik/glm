package info.gamed.glm.entity;

import java.time.Instant;
import java.util.List;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * JPA entity for the "game" table. Holds a game's board dimensions, the two players and their colours,
 * the boundary-condition mode and its cells.
 *
 * Games are not exposed as a writable REST collection; detailed game data is served read-only through
 * GameController (/api/games/{id}/details).
 *
 * @author Z@
 */
@Entity
@Table(
    name = "game",
    indexes = {
        @Index(name = "idx_game_player1_id", columnList = "player1_id"),
        @Index(name = "idx_game_player2_id", columnList = "player2_id"),
        // Serves the daily cleanup (status = FINISHED AND finish_date_time < cutoff) and, via its 'status'
        // prefix, the active/joinable lookups.
        @Index(name = "idx_game_status_finish", columnList = "status, finish_date_time")
    }
)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /** When the game entity was created (the WAITING game appeared). */
    @Column(name = "creation_date_time", nullable = false)
    private Instant creationDateTime;

    /** When the game started, i.e. the second player joined and it became ACTIVE. Null until then. */
    @Column(name = "start_date_time")
    private Instant startDateTime;

    /** When the game finished (won/drawn or a player exited). Null while it is ongoing. */
    @Column(name = "finish_date_time")
    private Instant finishDateTime;

    /**
     * It was decided to add this annotation @Column only if there is a need to change default length (255) or other default params (nullable, unique etc.)
     * For most of the cases the default approach one should work.
     */
    @Column(name = "game_name", nullable = true, length = 128, unique = false) 
    private String gameName;
    
    @Column(name = "game_x_dimension", nullable = false, unique = false)
    private int gameXDimension;
    
    @Column(name = "game_y_dimension", nullable = false, unique = false)
    private int gameYDimension;
    
    /**
     * Html color of the player1 in this specific game.
     */
    @Column(name = "player1_color", nullable = false, length = 7, unique = false)
    private String player1Color;

    /**
     * Html color of the player2 in this specific game. Null while the game is still waiting for a second
     * player to join (player2 is only set, together with this colour, on join).
     */
    @Column(name = "player2_color", nullable = true, length = 7, unique = false)
    private String player2Color;
    
    /**
     * Boundary Condition Mode field
     * 1 - periodic boundary conditions (already implemented) - cell should go over the edge of the game field and appear on other side
     * 2 - stop on edge (already implemented) - cells should die when reaching the edge of the game field.
     * 3 - object which reach the edge goes away. - cells outside of the game field are still considered as existing (at least for 1 cell). This is needed to make the effect that the object goes away.
     */
    @Column(name = "boundary_condition_mode", nullable = false)
    private int bcMode;
    
    public static final int BOUNDARY_CONDITION_PERIODIC = 1;
    public static final int BOUNDARY_CONDITION_STOP_ON_EDGE = 2;
    public static final int BOUNDARY_CONDITION_GO_TO_INFINITY = 3;

    /** Lifecycle status. Defaults to WAITING on creation; set to ACTIVE on join and FINISHED when it ends. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameStatus status = GameStatus.WAITING;

    /** winnerId value meaning the game ended in a draw. */
    public static final long WINNER_DRAW = -1L;

    /** When the game ends: the winning player's id, or WINNER_DRAW for a draw; null while it is ongoing. */
    @Column(name = "winner_id")
    private Long winnerId;

    /** Consecutive iterations with no change to the board; used to auto-finish a stalled game. */
    @Column(name = "stale_iterations", nullable = false)
    private int staleIterations = 0;

    /**
     * "Accumulated cell" credits each player may spend to place cells during the game. Each processed
     * iteration grants +1 credit (capped by gml.max-accumulated-cells); placing a cell costs 1 credit, so a
     * whole known object costs one credit per cell. A player who does not place for several ticks banks the
     * credits and can then drop a multi-cell object (e.g. a 5-cell glider needs 5 saved credits). This
     * replaced the old one-cell-per-iteration flags. */
    @Column(name = "player1_accumulated_cells", nullable = false)
    private int player1AccumulatedCells = 0;

    @Column(name = "player2_accumulated_cells", nullable = false)
    private int player2AccumulatedCells = 0;

    @ManyToOne(fetch = FetchType.EAGER) // Eager loading for players
    @JoinColumn(name = "player1_id")
    private Player player1;

    @ManyToOne(fetch = FetchType.EAGER) // Eager loading for players
    @JoinColumn(name = "player2_id")
    private Player player2;

    @OneToMany(mappedBy = "game", fetch = FetchType.LAZY) // Lazy loading for cells
    private List<Cell> cells;
    
    private String manager;

    protected Game() {
    }

    public Game(String gameName, int gameXDimension, int gameYDimension, String player1Color, String player2Color, Player player1, Player player2, String manager, int bcMode) {
        this.gameName = gameName;
        this.gameXDimension = gameXDimension;
        this.gameYDimension = gameYDimension;
        this.player1Color = player1Color;
        this.player2Color = player2Color;
        this.player1 = player1;
        this.player2 = player2;
        this.manager = manager;
        this.bcMode = bcMode;
    }

    @Override
    public String toString() {
        return String.format("Game[id=%d, creation_date_time='%s', game_name='%s', player1=%s, player2=%s, gameXDimension=%s, gameYDimension=%s]",
                id, creationDateTime, gameName,
                player1 != null ? player1.getId() : null,
                player2 != null ? player2.getId() : null,
                gameXDimension, gameYDimension);
    }

    public String getPlayer1Color() {
        return player1Color;
    }

    public void setPlayer1Color(String player1Color) {
        this.player1Color = player1Color;
    }

    public String getPlayer2Color() {
        return player2Color;
    }

    public void setPlayer2Color(String player2Color) {
        this.player2Color = player2Color;
    }

    public List<Cell> getCells() {
        return cells;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreationDateTime() {
        return creationDateTime;
    }

    public void setCreationDateTime(Instant creationDateTime) {
        this.creationDateTime = creationDateTime;
    }

    public Instant getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(Instant startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Instant getFinishDateTime() {
        return finishDateTime;
    }

    public void setFinishDateTime(Instant finishDateTime) {
        this.finishDateTime = finishDateTime;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }
    
    public Player getPlayer1() {
        return player1;
    }

    public void setPlayer1(Player player1) {
        this.player1 = player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public void setPlayer2(Player player2) {
        this.player2 = player2;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }
    
    public int getGameXDimension() {
        return gameXDimension;
    }

    public void setGameXDimension(int gameXDimension) {
        this.gameXDimension = gameXDimension;
    }
    
    public int getGameYDimension() {
        return gameYDimension;
    }

    public void setGameYDimension(int gameYDimension) {
        this.gameYDimension = gameYDimension;
    }

    public int getBcMode() {
        return bcMode;
    }

    public void setBcMode(int bcMode) {
        this.bcMode = bcMode;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
    }

    public int getStaleIterations() {
        return staleIterations;
    }

    public void setStaleIterations(int staleIterations) {
        this.staleIterations = staleIterations;
    }

    public int getPlayer1AccumulatedCells() {
        return player1AccumulatedCells;
    }

    public void setPlayer1AccumulatedCells(int player1AccumulatedCells) {
        this.player1AccumulatedCells = player1AccumulatedCells;
    }

    public int getPlayer2AccumulatedCells() {
        return player2AccumulatedCells;
    }

    public void setPlayer2AccumulatedCells(int player2AccumulatedCells) {
        this.player2AccumulatedCells = player2AccumulatedCells;
    }
}