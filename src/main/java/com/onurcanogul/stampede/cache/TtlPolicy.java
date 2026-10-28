package com.onurcanogul.stampede.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface TtlPolicy {

    Duration next();

    static TtlPolicy fixed(Duration ttl) {
        return () -> ttl;
    }

    static TtlPolicy jittered(Duration base, Duration maxJitter) {
        long baseMillis = base.toMillis();
        long jitterBound = maxJitter.toMillis() + 1;
        return () -> Duration.ofMillis(baseMillis + ThreadLocalRandom.current().nextLong(jitterBound));
    }
}
