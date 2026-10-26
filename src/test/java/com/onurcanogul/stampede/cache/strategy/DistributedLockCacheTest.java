package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.cache.lock.RedisLock;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.domain.Product;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockCacheTest extends StrategyTestSupport {

    @Test
    void concurrentMissesAcrossInstancesCollapseIntoOneQuery() {
        SimulatedDatabase database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        RedisLock lock = new RedisLock(redis);
        List<DistributedLockCache> instances = List.of(
                new DistributedLockCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL), lock),
                new DistributedLockCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL), lock),
                new DistributedLockCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL), lock));

        List<Product> results = concurrentGets(60, i -> instances.get(i % instances.size()));

        assertEquals(1, database.stats().total());
        assertTrue(results.stream().allMatch(PRODUCT::equals));
        assertNull(redis.get(CacheKeys.lock(CacheKeys.product(PRODUCT_ID))), "lock should be released");
    }

    @Test
    void waitersFallBackToTheDatabaseWhenTheWaitBudgetRunsOut() {
        SimulatedDatabase database = new SimulatedDatabase(Duration.ofMillis(300));
        database.seed(PRODUCT);
        RedisLock lock = new RedisLock(redis);
        DistributedLockCache cache = new DistributedLockCache(
                redis, database.connectionPool(10), TtlPolicy.fixed(TTL), lock, Duration.ofMillis(100));

        List<Product> results = concurrentGets(5, i -> cache);

        assertTrue(database.stats().total() > 1, "waiters should have given up and loaded themselves");
        assertTrue(results.stream().allMatch(PRODUCT::equals));
    }
}
