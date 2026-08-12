package info.gamed.glm.scheduledtask;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import info.gamed.glm.repository.CellRepository;

/**
 * Daily maintenance: deletes the cells of games that finished more than 24 hours ago. The game rows are
 * kept (with their timestamps, for stats). Auto-finished games keep their final board so players can see
 * how it ended; this purge reclaims that space a day later.
 * @author Z@
 */
@Component
public class FinishedGameCleanup {

    private static final Logger log = LoggerFactory.getLogger(FinishedGameCleanup.class);

    /** Keep a finished game's cells for this long before purging them. */
    private static final long RETENTION_HOURS = 24;

    private final CellRepository cellRepository;

    public FinishedGameCleanup(CellRepository cellRepository) {
        this.cellRepository = cellRepository;
    }

    /**
     * Runs at 08:00 Europe/Amsterdam every day (off-peak for EU and US). This is Spring's in-process
     * scheduler (the same mechanism as the game tick), NOT the OS cron - the cron string is parsed by Spring.
     *
     * NOTE (single scheduler thread): by default Spring runs all @Scheduled tasks on ONE thread, shared with
     * the 500ms game tick. This purge is a single fast bulk delete, so at worst it delays one tick slightly.
     * If that ever becomes a problem, give scheduled tasks their own threads via
     * 'spring.task.scheduling.pool.size=2' (or a dedicated TaskScheduler bean).
     *
     * NOTE (multiple instances): @Scheduled fires on EVERY running instance, so with more than one instance
     * this would run N times in parallel. If the app is ever scaled out, guard it with a distributed lock
     * (e.g. ShedLock) or run it only on an elected leader.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Amsterdam")
    public void purgeOldFinishedGameCells() {
        Instant cutoff = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);
        int deleted = cellRepository.deleteCellsOfGamesFinishedBefore(cutoff);
        log.info("Finished-game cell cleanup: removed {} cells of games finished before {}", deleted, cutoff);
    }
}
