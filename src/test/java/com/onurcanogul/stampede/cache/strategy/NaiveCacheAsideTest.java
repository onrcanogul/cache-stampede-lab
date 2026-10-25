package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.domain.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaiveCacheAsideTest extends StrategyTestSupport {

    @Test
    void secondReadIsServedFromRedis() {
        SimulatedDatabase database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        NaiveCacheAside cache = new NaiveCacheAside(redis, database.connectionPool(10), TtlPolicy.fixed(TTL));

        assertEquals(PRODUCT, cache.get(PRODUCT_ID));
        assertEquals(PRODUCT, cache.get(PRODUCT_ID));

        assertEquals(1, database.stats().total());
        assertEquals(PRODUCT.encode(), redis.get(CacheKeys.product(PRODUCT_ID)));
    }

    @Test
    void everyConcurrentMissHitsTheDatabase() {
        SimulatedDatabase database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        NaiveCacheAside cache = new NaiveCacheAside(redis, database.connectionPool(10), TtlPolicy.fixed(TTL));

        List<Product> results = concurrentGets(20, i -> cache);

        assertEquals(20, database.stats().total());
        assertEquals(10, database.stats().maxInFlight());
        assertTrue(results.stream().allMatch(PRODUCT::equals));
    }
}
