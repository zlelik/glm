package info.gamed.glm.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Collection of reusable methods.
 * @author Z@
 */
public class GLMUtils {

    /**
     * Generates a uniformly random number between min and max (both inclusive).
     *
     * Uses {@link ThreadLocalRandom}: each thread has its own generator (no lock contention when many
     * games are processed in parallel) and the bounded method avoids hand-rolled casts.
     *
     * @param min minimal value (inclusive)
     * @param max maximum value (inclusive); must be {@code >= min}
     * @return a random int in [min, max]
     */
    public static int getRandomNumber(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Max must be greater than or equal to Min");
        }
        // nextInt's upper bound is exclusive, hence max + 1 for an inclusive max.
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Generates a uniformly random number between min and max (both inclusive), for the full long range.
     *
     * @param min minimal value (inclusive)
     * @param max maximum value (inclusive); must be {@code >= min}
     * @return a random long in [min, max]
     */
    public static long getRandomNumber(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("Max must be greater than or equal to Min");
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
