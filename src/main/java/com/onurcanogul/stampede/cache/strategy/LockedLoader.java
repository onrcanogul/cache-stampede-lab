package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.lock.RedisLock;
import com.onurcanogul.stampede.redis.RedisClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Fills a missing key once across all instances: the lock holder loads,
 * everyone else polls the cache until it shows up or the wait budget runs out.
 */
final class LockedLoader {

    static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(5);
    static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(2);

    private static final long POLL_INTERVAL_MILLIS = 20;
    private static final long POLL_JITTER_MILLIS = 10;

    private final RedisClient redis;
    private final RedisLock lock;
    private final Duration lockTtl;
    private final Duration maxWait;

    LockedLoader(RedisClient redis, RedisLock lock) {
        this(redis, lock, DEFAULT_LOCK_TTL, DEFAULT_MAX_WAIT);
    }

    LockedLoader(RedisClient redis, RedisLock lock, Duration lockTtl, Duration maxWait) {
        this.redis = redis;
        this.lock = lock;
        this.lockTtl = lockTtl;
        this.maxWait = maxWait;
    }

    String load(String key, Supplier<String> loadAndStore) {
        String lockKey = CacheKeys.lock(key);
        long deadline = System.nanoTime() + maxWait.toNanos();
        while (true) {
            String cached = redis.get(key);
            if (cached != null) {
                return cached;
            }

            Optional<String> token = lock.tryAcquire(lockKey, lockTtl);
            if (token.isPresent()) {
                try {
                    String again = redis.get(key);
                    return again != null ? again : loadAndStore.get();
                } finally {
                    lock.release(lockKey, token.get());
                }
            }

            if (System.nanoTime() >= deadline) {
                return loadAndStore.get();
            }
            sleepBeforeRetry();
        }
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS + ThreadLocalRandom.current().nextLong(POLL_JITTER_MILLIS + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the cache to fill", e);
        }
    }
}
