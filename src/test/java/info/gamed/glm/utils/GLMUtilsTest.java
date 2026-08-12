package info.gamed.glm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GLMUtilsTest {

    @Test
    void intStaysWithinInclusiveRange() {
        for (int i = 0; i < 10_000; i++) {
            int r = GLMUtils.getRandomNumber(3, 7);
            assertTrue(r >= 3 && r <= 7, "out of range: " + r);
        }
    }

    @Test
    void intMinEqualsMaxReturnsThatValue() {
        assertEquals(5, GLMUtils.getRandomNumber(5, 5));
    }

    @Test
    void intThrowsWhenMinGreaterThanMax() {
        assertThrows(IllegalArgumentException.class, () -> GLMUtils.getRandomNumber(10, 1));
    }

    @Test
    void longStaysWithinInclusiveRange() {
        for (int i = 0; i < 10_000; i++) {
            long r = GLMUtils.getRandomNumber(1L, 2L);
            assertTrue(r == 1L || r == 2L, "out of range: " + r);
        }
    }

    @Test
    void longThrowsWhenMinGreaterThanMax() {
        assertThrows(IllegalArgumentException.class, () -> GLMUtils.getRandomNumber(10L, 1L));
    }

    /**
     * Regression test for the former {@code (int)} truncation bug: for a range wider than
     * Integer.MAX_VALUE the old code capped the result at ~2.1 billion, so it could never reach the top
     * of the range. The long version must span the full range.
     */
    @Test
    void longRangeWiderThanIntIsNotTruncated() {
        long min = 0L;
        long max = 10_000_000_000L; // > Integer.MAX_VALUE (~2.147e9)
        boolean reachedBeyondIntMax = false;
        for (int i = 0; i < 1_000; i++) {
            long r = GLMUtils.getRandomNumber(min, max);
            assertTrue(r >= min && r <= max, "out of range: " + r);
            if (r > Integer.MAX_VALUE) {
                reachedBeyondIntMax = true;
            }
        }
        assertTrue(reachedBeyondIntMax,
                "long random must reach beyond Integer.MAX_VALUE (this failed with the old (int) cast)");
    }
}
