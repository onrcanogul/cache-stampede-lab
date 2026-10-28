package com.onurcanogul.stampede.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtlPolicyTest {

    @Test
    void fixedPolicyAlwaysReturnsTheSameTtl() {
        TtlPolicy policy = TtlPolicy.fixed(Duration.ofSeconds(3));

        for (int i = 0; i < 100; i++) {
            assertEquals(Duration.ofSeconds(3), policy.next());
        }
    }

    @Test
    void jitteredPolicyStaysWithinBaseAndBasePlusMaxJitter() {
        Duration base = Duration.ofSeconds(3);
        Duration maxJitter = Duration.ofSeconds(1);
        TtlPolicy policy = TtlPolicy.jittered(base, maxJitter);

        Set<Long> distinct = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            Duration ttl = policy.next();
            assertTrue(ttl.compareTo(base) >= 0, "below base: " + ttl);
            assertTrue(ttl.compareTo(base.plus(maxJitter)) <= 0, "above base + jitter: " + ttl);
            distinct.add(ttl.toMillis());
        }
        assertTrue(distinct.size() > 100, "jitter should spread TTLs, got " + distinct.size() + " distinct values");
    }
}
