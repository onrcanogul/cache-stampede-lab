package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Collapses concurrent misses for the same key into one load per JVM.
 * Equivalent to Spring's {@code @Cacheable(sync = true)}.
 */
public final class SingleFlightCache implements ProductCache {

    private final RedisClient redis;
    private final ProductRepository repository;
    private final TtlPolicy ttl;
    private final ConcurrentMap<Long, CompletableFuture<Product>> inFlight = new ConcurrentHashMap<>();

    public SingleFlightCache(RedisClient redis, ProductRepository repository, TtlPolicy ttl) {
        this.redis = redis;
        this.repository = repository;
        this.ttl = ttl;
    }

    @Override
    public Product get(long id) {
        String key = CacheKeys.product(id);
        String cached = redis.get(key);
        if (cached != null) {
            return Product.decode(cached);
        }

        CompletableFuture<Product> mine = new CompletableFuture<>();
        CompletableFuture<Product> existing = inFlight.putIfAbsent(id, mine);
        if (existing != null) {
            return existing.join();
        }
        try {
            Product product = loadAndCache(id, key);
            mine.complete(product);
            return product;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(id, mine);
        }
    }

    private Product loadAndCache(long id, String key) {
        Product product = repository.findById(id);
        redis.set(key, product.encode(), ttl.next());
        return product;
    }
}
