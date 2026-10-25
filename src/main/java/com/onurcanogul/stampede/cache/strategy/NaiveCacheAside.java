package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

public final class NaiveCacheAside implements ProductCache {

    private final RedisClient redis;
    private final ProductRepository repository;
    private final TtlPolicy ttl;

    public NaiveCacheAside(RedisClient redis, ProductRepository repository, TtlPolicy ttl) {
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
        Product product = repository.findById(id);
        redis.set(key, product.encode(), ttl.next());
        return product;
    }
}
