package info.gamed.glm.gamelogic;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import info.gamed.glm.config.GameProperties;
import info.gamed.glm.notification.GameNotifier;
import info.gamed.glm.repository.GameRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Processes all games each tick on a bounded thread pool - one task per game, at most
 * game-processor-threads running concurrently. Blocks until every game is done so runs never overlap.
 * @author Z@
 */
@Component
public class GameLogicProcessor {
    private static final Logger log = LoggerFactory.getLogger(GameLogicProcessor.class);

    // DB connection pool size - used only to warn on misconfiguration. Defaults to HikariCP's default (10).
    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int dbPoolSize;

    private final GameRepository gameRepository;
    private final SingleGameProcessor singleGameProcessor;
    private final GameProperties gameProperties;
    private final GameNotifier gameNotifier;

    private ExecutorService executor;

    public GameLogicProcessor(GameRepository gameRepository, SingleGameProcessor singleGameProcessor,
                              GameProperties gameProperties, GameNotifier gameNotifier) {
        this.gameRepository = gameRepository;
        this.singleGameProcessor = singleGameProcessor;
        this.gameProperties = gameProperties;
        this.gameNotifier = gameNotifier;
    }

    @PostConstruct
    void initExecutor() {
        int logicalProcessors = Runtime.getRuntime().availableProcessors();
        // > 0 = used as-is; 0, negative or absent => auto = (logical CPUs * 2).
        int gameProcessorThreads = gameProperties.getGameProcessorThreads();
        int threads = (gameProcessorThreads > 0) ? gameProcessorThreads : logicalProcessors * 2;
        this.executor = Executors.newFixedThreadPool(threads);
        log.info(String.format("Game processor pool size: %s (configured gml.game-processor-threads=%s, logical processors=%s)",
                threads, gameProcessorThreads, logicalProcessors));
        if (threads > dbPoolSize) {
            log.warn(String.format("game-processor-threads (%s) is greater than the DB connection pool size (%s). "
                    + "Excess worker threads will block waiting for a connection - processing will be slower, not broken.",
                    threads, dbPoolSize));
        }
    }

    @PreDestroy
    void shutdownExecutor() {
        if (this.executor != null) {
            this.executor.shutdown();
        }
    }

    /**
     * Processes every game once, one task per game, at most game-processor-threads concurrently. Blocks
     * until all games are done (the pool queues the rest), so a game is processed by exactly one thread per
     * run and the scheduler never starts an overlapping run.
     */
    public void processAllGamesData() {
        long startTime = System.currentTimeMillis();
        log.debug("processAllGamesData started");
        // Only games that have both players; a game waiting for player2 stays frozen until someone joins.
        List<Long> ids = gameRepository.findActiveGameIds();
        log.debug(String.format("processAllGamesData ids.size(): %s", ids.size()));
        if (ids.isEmpty()) {
            return;
        }

        // One task per game. processSingleGame runs each in its own transaction, so a failing game rolls
        // back only itself and is logged without aborting the others.
        List<Callable<Void>> tasks = ids.stream()
                .map(id -> (Callable<Void>) () -> {
                    // processSingleGame's @Transactional has committed by the time it returns, so when it
                    // reports the game finished we notify clients here (post-commit) to leave the game.
                    if (singleGameProcessor.processSingleGame(id)) {
                        gameNotifier.gameFinished(id);
                    }
                    return null;
                })
                .toList();

        try {
            // invokeAll submits all tasks, runs at most pool-size concurrently (queues the rest) and BLOCKS
            // until every task finishes - this is the queue + the completion barrier in a single call.
            List<Future<Void>> results = executor.invokeAll(tasks);
            for (Future<Void> result : results) {
                try {
                    result.get(); // surface per-game failures for logging
                } catch (Exception e) {
                    log.error("processAllGamesData a game failed", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("processAllGamesData interrupted while processing games");
        }

        log.debug("processAllGamesData finished");
        long endTime = System.currentTimeMillis();
        log.debug(String.format("processAllGamesData finished. Total time: [%ss]", (endTime - startTime) / 1000.0));
    }
}
