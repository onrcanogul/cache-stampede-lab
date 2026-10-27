package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.cache.lock.RedisLock;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.time.ManualClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleWhileRevalidateCacheTest extends StrategyTestSupport {

    private static final Duration SOFT_TTL = Duration.ofSeconds(2);
    private static final Duration HARD_TTL = Duration.ofSeconds(10);

    private SimulatedDatabase database;
    private ManualClock clock;
    private ExecutorService refresher;
    private StaleWhileRevalidateCache cache;

    @BeforeEach
    void setUp() {
        database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        clock = ManualClock.startingNow();
        refresher = Executors.newVirtualThreadPerTaskExecutor();
        cache = new StaleWhileRevalidateCache(redis, database.connectionPool(10), SOFT_TTL,
                TtlPolicy.fixed(HARD_TTL), new RedisLock(redis), refresher, clock);
    }

    @AfterEach
    void tearDown() {
        refresher.close();
    }

    @Test
    void freshValueIsServedWithoutTouchingTheDatabase() {
        cache.get(PRODUCT_ID);
        clock.advance(SOFT_TTL.minusMillis(1));

        assertEquals(PRODUCT, cache.get(PRODUCT_ID));
        assertEquals(1, database.stats().total());
    }

    @Test
    void staleValueIsServedImmediatelyAndRefreshedOnceInTheBackground() {
        cache.get(PRODUCT_ID);
        Product updated = PRODUCT.withPrice(150);
        database.seed(updated);
        clock.advance(SOFT_TTL.plusMillis(1));

        long started = System.nanoTime();
        List<Product> results = concurrentGets(50, i -> cache);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        refresher.close();

        assertTrue(results.stream().allMatch(PRODUCT::equals), "stale reads should return the old value");
        assertTrue(elapsed.compareTo(QUERY_LATENCY) < 0, "stale reads must not wait for the database: " + elapsed);
        assertEquals(2, database.stats().total(), "one initial load plus exactly one refresh");
        assertEquals(updated, cache.get(PRODUCT_ID));
    }

    @Test
    void hardMissFallsBackToOneLockedReload() {
        List<Product> results = concurrentGets(30, i -> cache);

        assertEquals(1, database.stats().total());
        assertTrue(results.stream().allMatch(PRODUCT::equals));
    }
}
