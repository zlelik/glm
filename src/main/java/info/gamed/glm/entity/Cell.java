package info.gamed.glm.entity;

import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity for the "cell" table. One row represents one live cell; only live cells have a record.
 * A cell that dies has its row deleted, so the absence of a row means the cell is dead.
 *
 * Cells are not exposed as a standalone REST collection - they are read as part of a game's details
 * (see GameController) and created/removed by the game logic on the server.
 *
 * @author Z@
 */
@Entity
@Table(
    name = "cell",
    uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "x_position", "y_position"}),
    indexes = {
        @Index(name = "idx_cell_player_id", columnList = "player_id"),
        @Index(name = "idx_cell_game_id", columnList = "game_id")
    }
)
public class Cell {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    /**
     * x Coordinate of the cell. 
     */
    @Column(name = "x_position", nullable = false, unique = false)
    private int xPosition;

    /**
     * y Coordinate of the cell.  
     */
    @Column(name = "y_position", nullable = false, unique = false)
    private int yPosition;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;
    
    protected Cell() {
    }

    public Cell(int xPosition, int yPosition, Game game, Player player) {
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.game = game;
        this.player = player;
    }

    @Override
    public String toString() {
        return String.format("Cell[id=%d, x_position='%s', y_position='%s', player='%s', game='%s']", id, xPosition, yPosition, Optional.ofNullable(player).map(p -> String.valueOf(p.getId())).orElse("no-player"), game);
    }

    public Long getId() {
        return id;
    }

    public int getXPosition() {
        return xPosition;
    }

    public void setXPosition(int xPosition) {
        this.xPosition = xPosition;
    }

    public int getYPosition() {
        return yPosition;
    }

    public void setYPosition(int yPosition) {
        this.yPosition = yPosition;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

}