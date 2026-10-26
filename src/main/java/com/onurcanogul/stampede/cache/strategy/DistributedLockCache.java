package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.cache.lock.RedisLock;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

import java.time.Duration;

public final class DistributedLockCache implements ProductCache {

    private final RedisClient redis;
    private final ProductRepository repository;
    private final TtlPolicy ttl;
    private final LockedLoader loader;

    public DistributedLockCache(RedisClient redis, ProductRepository repository, TtlPolicy ttl, RedisLock lock) {
        this(redis, repository, ttl, lock, LockedLoader.DEFAULT_MAX_WAIT);
    }

    public DistributedLockCache(RedisClient redis, ProductRepository repository, TtlPolicy ttl, RedisLock lock,
                                Duration maxWait) {
        this.redis = redis;
        this.repository = repository;
        this.ttl = ttl;
        this.loader = new LockedLoader(redis, lock, LockedLoader.DEFAULT_LOCK_TTL, maxWait);
    }

    @Override
    public Product get(long id) {
        String key = CacheKeys.product(id);
        String cached = redis.get(key);
        if (cached != null) {
            return Product.decode(cached);
        }
        return Product.decode(loader.load(key, () -> loadAndStore(id, key)));
    }

    private String loadAndStore(long id, String key) {
        String encoded = repository.findById(id).encode();
        redis.set(key, encoded, ttl.next());
        return encoded;
    }
}
