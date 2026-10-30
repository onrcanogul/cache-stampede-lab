package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.RedisTestSupport;
import com.onurcanogul.stampede.scenario.InvalidationRaceScenario.Mode;
import com.onurcanogul.stampede.scenario.InvalidationRaceScenario.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvalidationRaceScenarioTest extends RedisTestSupport {

    private final InvalidationRaceScenario scenario = new InvalidationRaceScenario();

    @Test
    void deleteOnWriteLeavesTheOldRowInTheCache() {
        Outcome outcome = scenario.play(redis, Mode.DELETE_ON_WRITE);

        assertEquals(150, outcome.databasePrice());
        assertEquals(100, outcome.cachedPrice());
        assertTrue(outcome.stale());
    }

    @Test
    void versionedWriteKeepsTheNewRowInTheCache() {
        Outcome outcome = scenario.play(redis, Mode.VERSIONED_WRITE);

        assertEquals(150, outcome.databasePrice());
        assertEquals(150, outcome.cachedPrice());
        assertFalse(outcome.stale());
    }
}
