package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.RedisTestSupport;
import com.onurcanogul.stampede.scenario.ExpiryBurstScenario.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiryBurstScenarioTest extends RedisTestSupport {

    private static final int INSTANCES = 3;
    private static final int CONNECTIONS = 10;
    private static final Duration QUERY_LATENCY = Duration.ofMillis(100);
    private static final int REQUESTS = 90;

    private ExpiryBurstScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = new ExpiryBurstScenario(new ExpiryBurstScenario.Settings(
                INSTANCES, CONNECTIONS, QUERY_LATENCY, REQUESTS, Duration.ofSeconds(30)));
    }

    @Test
    void naiveCacheAsideSendsEveryRequestToTheDatabase() {
        Result result = scenario.measure(redis, Strategy.NAIVE);

        assertEquals(REQUESTS, result.databaseQueries());
        assertEquals(INSTANCES * CONNECTIONS, result.maxConcurrentQueries());
        assertTrue(result.latency().percentile(99).compareTo(QUERY_LATENCY.multipliedBy(2)) >= 0,
                "the last wave must queue behind the first: " + result.latency().percentile(99));
    }

    @Test
    void singleFlightSendsOneQueryPerInstance() {
        Result result = scenario.measure(redis, Strategy.SINGLE_FLIGHT);

        assertEquals(INSTANCES, result.databaseQueries());
    }

    @Test
    void distributedLockSendsOneQueryForTheWholeCluster() {
        Result result = scenario.measure(redis, Strategy.DISTRIBUTED_LOCK);

        assertEquals(1, result.databaseQueries());
    }

    @Test
    void staleWhileRevalidateSendsOneQueryAndNobodyWaitsForIt() {
        Result result = scenario.measure(redis, Strategy.STALE_WHILE_REVALIDATE);

        assertEquals(1, result.databaseQueries());
        assertTrue(result.latency().percentile(99).compareTo(QUERY_LATENCY) < 0,
                "stale reads should not wait for the database: " + result.latency().percentile(99));
    }
}
