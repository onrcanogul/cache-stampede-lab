package com.onurcanogul.stampede;

import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.redis.RedisConfig;
import com.onurcanogul.stampede.redis.RedisException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class RedisTestSupport {

    protected static RedisClient redis;

    @BeforeAll
    static void connectToRedis() {
        RedisConfig config = RedisConfig.fromEnvironment().withPoolSize(16);
        try {
            redis = RedisClient.connect(config);
        } catch (RedisException e) {
            Assumptions.abort("Redis is not reachable at " + config.address() + "; start it with `docker compose up -d`");
        }
    }

    @BeforeEach
    void flushRedis() {
        redis.flushDb();
    }

    @AfterAll
    static void disconnect() {
        if (redis != null) {
            redis.close();
        }
    }
}
