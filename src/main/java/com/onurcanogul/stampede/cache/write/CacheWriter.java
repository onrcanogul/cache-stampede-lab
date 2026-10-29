package com.onurcanogul.stampede.cache.write;

import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;

import java.time.Duration;

@FunctionalInterface
public interface CacheWriter {

    boolean write(String key, Product product, Duration ttl);

    static CacheWriter plain(RedisClient redis) {
        return (key, product, ttl) -> {
            redis.set(key, product.encode(), ttl);
            return true;
        };
    }
}
