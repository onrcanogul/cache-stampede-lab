package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.time.ManualClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbabilisticEarlyRefreshCacheTest extends StrategyTestSupport {

    private static final Duration LIFETIME = Duration.ofSeconds(2);
    private static final double FIXED_RANDOM = 0.5;

    private SimulatedDatabase database;
    private ManualClock clock;
    private ProbabilisticEarlyRefreshCache cache;

    @BeforeEach
    void setUp() {
        database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        clock = ManualClock.startingNow();
        cache = new ProbabilisticEarlyRefreshCache(redis, database.connectionPool(10), TtlPolicy.fixed(LIFETIME), 1.0,
                clock, () -> FIXED_RANDOM);
    }

    @Test
    void farFromExpiryTheCachedValueIsUsed() {
        cache.get(PRODUCT_ID);
        clock.advance(Duration.ofMillis(500));

        for (int i = 0; i < 100; i++) {
            assertEquals(PRODUCT, cache.get(PRODUCT_ID));
        }

        assertEquals(1, database.stats().total());
    }

    @Test
    void closeToExpiryTheValueIsRecomputedBeforeItIsGone() {
        cache.get(PRODUCT_ID);
        // delta ~= 50 ms, beta = 1, random = 0.5 => head ~= 35 ms, so 10 ms before expiry is inside the window
        clock.advance(LIFETIME.minusMillis(10));

        cache.get(PRODUCT_ID);

        assertEquals(2, database.stats().total());
    }
}
