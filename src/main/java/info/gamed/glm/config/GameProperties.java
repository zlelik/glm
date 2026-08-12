package info.gamed.glm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Type-safe, validated game settings bound from the {@code gml.*} properties. Replaces the scattered
 * {@code @Value("${gml...}")} injections with a single documented place. Relaxed binding maps the
 * recommended kebab-case keys (e.g. {@code gml.delete-cells-batch-size}) onto these camelCase fields, and
 * lets each value be overridden by an environment variable (e.g. {@code GML_DELETECELLSBATCHSIZE}) in prod.
 * @author Z@
 */
@ConfigurationProperties(prefix = "gml")
@Validated
public class GameProperties {

    /** Chunk size for the custom batched cell delete (gml.delete-cells-batch-size). */
    @Min(1)
    private int deleteCellsBatchSize = 1000;

    /** Max games processed concurrently; 0 or less means auto = (logical processors * 2). */
    private int gameProcessorThreads = 0;

    /**
     * Auto-finish a game after this many consecutive iterations with no change to the board (the player
     * with more live cells wins). 0 or less disables this (a stable game then runs forever).
     */
    private int maxStaleIterations = 10;

    /**
     * Upper bound on the "accumulated cell" credits a player can bank (each iteration grants +1). Caps how
     * large an object a player can save up for and stops an idle player hoarding a whole-board army. Should
     * be at least as large as the biggest known object a player might place (currently 88 cells). 0 or less
     * means unbounded.
     */
    private int maxAccumulatedCells = 100;

    public int getDeleteCellsBatchSize() {
        return deleteCellsBatchSize;
    }

    public void setDeleteCellsBatchSize(int deleteCellsBatchSize) {
        this.deleteCellsBatchSize = deleteCellsBatchSize;
    }

    public int getGameProcessorThreads() {
        return gameProcessorThreads;
    }

    public void setGameProcessorThreads(int gameProcessorThreads) {
        this.gameProcessorThreads = gameProcessorThreads;
    }

    public int getMaxStaleIterations() {
        return maxStaleIterations;
    }

    public void setMaxStaleIterations(int maxStaleIterations) {
        this.maxStaleIterations = maxStaleIterations;
    }

    public int getMaxAccumulatedCells() {
        return maxAccumulatedCells;
    }

    public void setMaxAccumulatedCells(int maxAccumulatedCells) {
        this.maxAccumulatedCells = maxAccumulatedCells;
    }
}
