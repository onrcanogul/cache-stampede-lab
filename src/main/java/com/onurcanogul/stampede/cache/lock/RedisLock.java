package com.onurcanogul.stampede.cache.lock;

import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.redis.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SET NX PX based lock. It limits duplicate work; it is not a correctness
 * guarantee, so never protect money with it.
 */
public final class RedisLock {

    private static final RedisScript RELEASE = RedisScript.fromResource("lua/release-lock.lua");

    private final RedisClient redis;

    public RedisLock(RedisClient redis) {
        this.redis = redis;
    }

    public Optional<String> tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        return redis.setNx(key, token, ttl) ? Optional.of(token) : Optional.empty();
    }

    public boolean release(String key, String token) {
        return Long.valueOf(1).equals(redis.eval(RELEASE, List.of(key), List.of(token)));
    }
}
