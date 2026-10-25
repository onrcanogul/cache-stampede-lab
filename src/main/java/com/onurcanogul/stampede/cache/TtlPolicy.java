package com.onurcanogul.stampede.cache;

import java.time.Duration;

@FunctionalInterface
public interface TtlPolicy {

    Duration next();

    static TtlPolicy fixed(Duration ttl) {
        return () -> ttl;
    }
}
