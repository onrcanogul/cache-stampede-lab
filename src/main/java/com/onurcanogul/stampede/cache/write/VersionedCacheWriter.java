package com.onurcanogul.stampede.cache.write;

import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.redis.RedisScript;

import java.time.Duration;
import java.util.List;

public final class VersionedCacheWriter implements CacheWriter {

    private static final RedisScript VERSIONED_SET = RedisScript.fromResource("lua/versioned-set.lua");

    private final RedisClient redis;

    public VersionedCacheWriter(RedisClient redis) {
        this.redis = redis;
    }

    @Override
    public boolean write(String key, Product product, Duration ttl) {
        Object reply = redis.eval(
                VERSIONED_SET,
                List.of(key),
                List.of(Long.toString(product.version()), product.encode(), Long.toString(ttl.toMillis())));
        return Long.valueOf(1).equals(reply);
    }
}
