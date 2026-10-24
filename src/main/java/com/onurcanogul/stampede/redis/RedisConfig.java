package com.onurcanogul.stampede.redis;

import java.time.Duration;

public record RedisConfig(String host, int port, int poolSize, Duration timeout) {

    public static RedisConfig fromEnvironment() {
        String host = env("REDIS_HOST", "localhost");
        int port = Integer.parseInt(env("REDIS_PORT", "6379"));
        int poolSize = Integer.parseInt(env("REDIS_POOL_SIZE", "64"));
        return new RedisConfig(host, port, poolSize, Duration.ofSeconds(5));
    }

    public RedisConfig withPoolSize(int size) {
        return new RedisConfig(host, port, size, timeout);
    }

    public String address() {
        return host + ":" + port;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
