package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * XFetch (Vattani et al., VLDB 2015): every read recomputes the value early
 * with a probability that grows as expiry approaches and with how long the
 * last recomputation took. No locks, no background threads.
 */
public final class ProbabilisticEarlyRefreshCache implements ProductCache {

    private final RedisClient redis;
    private final ProductRepository repository;
    private final TtlPolicy ttl;
    private final double beta;
    private final Clock clock;
    private final DoubleSupplier random;

    public ProbabilisticEarlyRefreshCache(RedisClient redis, ProductRepository repository, TtlPolicy ttl,
                                          double beta, Clock clock) {
        this(redis, repository, ttl, beta, clock, () -> ThreadLocalRandom.current().nextDouble());
    }

    public ProbabilisticEarlyRefreshCache(RedisClient redis, ProductRepository repository, TtlPolicy ttl,
                                          double beta, Clock clock, DoubleSupplier random) {
        this.redis = redis;
        this.repository = repository;
        this.ttl = ttl;
        this.beta = beta;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public Product get(long id) {
        String key = CacheKeys.product(id);
        String raw = redis.get(key);
        if (raw == null) {
            return recompute(id, key);
        }
        Entry entry = Entry.decode(raw);
        double head = -entry.deltaMillis() * beta * Math.log(1 - random.getAsDouble());
        if (clock.millis() + head >= entry.expiresAt()) {
            return recompute(id, key);
        }
        return entry.product();
    }

    private Product recompute(long id, String key) {
        long started = System.nanoTime();
        Product product = repository.findById(id);
        long deltaMillis = Math.max(1, Duration.ofNanos(System.nanoTime() - started).toMillis());

        Duration lifetime = ttl.next();
        Entry entry = new Entry(clock.millis() + lifetime.toMillis(), deltaMillis, product);
        redis.set(key, entry.encode(), lifetime);
        return product;
    }

    private record Entry(long expiresAt, long deltaMillis, Product product) {

        String encode() {
            return expiresAt + "|" + deltaMillis + "|" + product.encode();
        }

        static Entry decode(String raw) {
            String[] fields = raw.split("\\|", 3);
            return new Entry(Long.parseLong(fields[0]), Long.parseLong(fields[1]), Product.decode(fields[2]));
        }
    }
}
