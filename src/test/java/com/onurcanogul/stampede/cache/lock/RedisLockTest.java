package com.onurcanogul.stampede.cache.lock;

import com.onurcanogul.stampede.RedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisLockTest extends RedisTestSupport {

    private static final String KEY = "product:1:lock";
    private static final Duration TTL = Duration.ofSeconds(5);

    private RedisLock lock;

    @BeforeEach
    void setUp() {
        lock = new RedisLock(redis);
    }

    @Test
    void secondAcquireFailsWhileLockIsHeld() {
        Optional<String> first = lock.tryAcquire(KEY, TTL);
        Optional<String> second = lock.tryAcquire(KEY, TTL);

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
    }

    @Test
    void releaseWithOwnTokenFreesTheLock() {
        String token = lock.tryAcquire(KEY, TTL).orElseThrow();

        assertTrue(lock.release(KEY, token));
        assertTrue(lock.tryAcquire(KEY, TTL).isPresent());
    }

    @Test
    void releaseWithSomeoneElsesTokenLeavesTheLockAlone() {
        String stale = lock.tryAcquire(KEY, Duration.ofMillis(50)).orElseThrow();
        sleep(Duration.ofMillis(80));
        String current = lock.tryAcquire(KEY, TTL).orElseThrow();

        assertFalse(lock.release(KEY, stale));
        assertEquals(current, redis.get(KEY));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
