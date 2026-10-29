package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.redis.RedisClient;

public interface Scenario {

    String id();

    String title();

    void run(RedisClient redis);
}
