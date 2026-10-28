package com.onurcanogul.stampede.cache;

import com.onurcanogul.stampede.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TtlJitterTest extends RedisTestSupport {

    private static final int KEYS = 500;
    private static final Duration BASE = Duration.ofSeconds(3);
    private static final Duration MAX_JITTER = Duration.ofSeconds(1);

    @Test
    void fixedTtlMakesEveryKeyExpireAtTheSameTime() {
        long spread = writeAndMeasureSpread(TtlPolicy.fixed(BASE));

        assertTrue(spread < 100, "fixed TTLs should expire together, spread was " + spread + " ms");
    }

    @Test
    void jitterSpreadsExpiryAcrossTheJitterWindow() {
        long spread = writeAndMeasureSpread(TtlPolicy.jittered(BASE, MAX_JITTER));

        assertTrue(spread > MAX_JITTER.toMillis() / 2, "jittered TTLs should spread out, spread was " + spread + " ms");
    }

    private long writeAndMeasureSpread(TtlPolicy policy) {
        for (long id = 1; id <= KEYS; id++) {
            redis.set(CacheKeys.product(id), "value", policy.next());
        }
        long[] remaining = LongStream.rangeClosed(1, KEYS).map(id -> redis.pttl(CacheKeys.product(id))).toArray();
        return LongStream.of(remaining).max().orElseThrow() - LongStream.of(remaining).min().orElseThrow();
    }
}
