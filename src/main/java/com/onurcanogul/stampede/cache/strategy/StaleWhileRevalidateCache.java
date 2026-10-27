package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.cache.lock.RedisLock;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * Two TTLs: the soft TTL lives inside the value and marks when the data is
 * stale; the hard TTL is the Redis key expiry and caps how long stale data may
 * be served. Stale reads return immediately and trigger a single background
 * refresh; a hard miss falls back to a locked reload.
 */
public final class StaleWhileRevalidateCache implements ProductCache {

    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(5);

    private final RedisClient redis;
    private final ProductRepository repository;
    private final Duration softTtl;
    private final TtlPolicy hardTtl;
    private final RedisLock lock;
    private final ExecutorService refresher;
    private final Clock clock;
    private final LockedLoader loader;

    public StaleWhileRevalidateCache(RedisClient redis, ProductRepository repository, Duration softTtl,
                                     TtlPolicy hardTtl, RedisLock lock, ExecutorService refresher, Clock clock) {
        if (softTtl.compareTo(hardTtl.next()) >= 0) {
            throw new IllegalArgumentException("soft TTL must be shorter than the hard TTL");
        }
        this.redis = redis;
        this.repository = repository;
        this.softTtl = softTtl;
        this.hardTtl = hardTtl;
        this.lock = lock;
        this.refresher = refresher;
        this.clock = clock;
        this.loader = new LockedLoader(redis, lock);
    }

    @Override
    public Product get(long id) {
        String key = CacheKeys.product(id);
        String raw = redis.get(key);
        if (raw == null) {
            return product(loader.load(key, () -> loadAndStore(id, key)));
        }
        if (clock.millis() < freshUntil(raw)) {
            return product(raw);
        }
        scheduleRefresh(id, key);
        return product(raw);
    }

    private void scheduleRefresh(long id, String key) {
        String refreshKey = CacheKeys.refresh(key);
        lock.tryAcquire(refreshKey, REFRESH_LOCK_TTL).ifPresent(token -> refresher.submit(() -> {
            try {
                loadAndStore(id, key);
            } finally {
                lock.release(refreshKey, token);
            }
        }));
    }

    private String loadAndStore(long id, String key) {
        Product product = repository.findById(id);
        String raw = (clock.millis() + softTtl.toMillis()) + "|" + product.encode();
        redis.set(key, raw, hardTtl.next());
        return raw;
    }

    private static long freshUntil(String raw) {
        return Long.parseLong(raw.substring(0, raw.indexOf('|')));
    }

    private static Product product(String raw) {
        return Product.decode(raw.substring(raw.indexOf('|') + 1));
    }
}
