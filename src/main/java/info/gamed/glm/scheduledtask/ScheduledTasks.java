package info.gamed.glm.scheduledtask;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import info.gamed.glm.gamelogic.GameLogicProcessor;

/**
 * Triggers the main game-state calculation on a custom cadence:
 *  - aim for a steady 500ms clock (next run starts 500ms after the previous run STARTED), so when a
 *    calculation finishes within 500ms the game updates every 500ms;
 *  - if a calculation overruns 500ms, the next run starts 50ms after it FINISHED - a small breather so the
 *    server is not hammered back-to-back. Worst case cadence = calculation time + 50ms.
 *
 * The next run time is always derived from the previous run's completion, so runs never overlap and a game
 * is never calculated by two threads at once (which would corrupt the cell data).
 * @author Z@
 */
@Component
public class ScheduledTasks implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    /** Target cadence in ms, measured start-to-start. */
    private static final long TARGET_PERIOD_MS = 500;
    /** Minimum breather in ms after a calculation that overran the target period. */
    private static final long MIN_GAP_AFTER_OVERRUN_MS = 50;

    private final GameLogicProcessor gameLogicHandler;

    public ScheduledTasks(GameLogicProcessor gameLogicHandler) {
        this.gameLogicHandler = gameLogicHandler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::recalculateGameState, this::nextRunTime);
    }

    /**
     * Computes when the next calculation should start.
     * next = max( lastStart + 500ms, lastFinish + 50ms ) expressed as a clamp:
     *  - finished within the 500ms window -> keep the steady 500ms clock (lastStart + 500ms);
     *  - overran the window               -> 50ms after it finished.
     */
    private Instant nextRunTime(TriggerContext context) {
        Instant lastStart = context.lastActualExecution();
        Instant lastFinish = context.lastCompletion();
        if (lastStart == null || lastFinish == null) {
            return Instant.now(); // first run - start immediately
        }
        Instant byClock = lastStart.plusMillis(TARGET_PERIOD_MS);
        if (!lastFinish.isAfter(byClock)) {
            return byClock; // finished within 500ms of starting -> steady 500ms clock
        }
        return lastFinish.plusMillis(MIN_GAP_AFTER_OVERRUN_MS); // overran -> small breather after completion
    }

    public void recalculateGameState() {
        log.debug("-----------------------------------------------");
        log.debug("recalculateGameState started");
        // NOTE (multiple instances): this tick runs in-process on EVERY running instance. With more than one
        // instance, each would process the games in parallel and corrupt each other's cell changes. If the
        // app is ever scaled out, this (and the daily FinishedGameCleanup) must run on a single node only -
        // e.g. via a distributed lock (ShedLock) or leader election.
        // Process all games synchronously; the trigger computes the next run from this run's completion,
        // so runs never overlap.
        try {
            gameLogicHandler.processAllGamesData();
        } catch (Exception e) {
            // Swallow (after logging) so the failure does not stop future runs. The next tick retries.
            log.error("recalculateGameState failed - will retry on next tick", e);
        }
        log.debug("recalculateGameState finished");
        log.debug("-----------------------------------------------");
    }
}
