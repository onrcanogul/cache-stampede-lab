package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.domain.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFlightCacheTest extends StrategyTestSupport {

    @Test
    void concurrentMissesOnOneInstanceCollapseIntoOneQuery() {
        SimulatedDatabase database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        SingleFlightCache cache = new SingleFlightCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL));

        List<Product> results = concurrentGets(50, i -> cache);

        assertEquals(1, database.stats().total());
        assertTrue(results.stream().allMatch(PRODUCT::equals));
    }

    @Test
    void collapsingIsPerInstance() {
        SimulatedDatabase database = new SimulatedDatabase(QUERY_LATENCY);
        database.seed(PRODUCT);
        List<SingleFlightCache> instances = List.of(
                new SingleFlightCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL)),
                new SingleFlightCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL)),
                new SingleFlightCache(redis, database.connectionPool(10), TtlPolicy.fixed(TTL)));

        concurrentGets(60, i -> instances.get(i % instances.size()));

        assertEquals(instances.size(), database.stats().total());
    }
}
