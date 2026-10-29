package com.onurcanogul.stampede.cache.write;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

public final class DeleteOnWriteProductWriter implements ProductWriter {

    private final ProductRepository repository;
    private final RedisClient redis;

    public DeleteOnWriteProductWriter(ProductRepository repository, RedisClient redis) {
        this.repository = repository;
        this.redis = redis;
    }

    @Override
    public void update(Product product) {
        repository.save(product);
        redis.del(CacheKeys.product(product.id()));
    }
}
