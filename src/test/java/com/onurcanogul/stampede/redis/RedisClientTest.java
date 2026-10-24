package com.onurcanogul.stampede.redis;

import com.onurcanogul.stampede.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisClientTest extends RedisTestSupport {

    @Test
    void pingReturnsPong() {
        assertEquals("PONG", redis.ping());
    }

    @Test
    void setThenGetRoundTripsUnicodeValues() {
        redis.set("greeting", "merhaba dünya|ünïcödé", Duration.ofSeconds(10));

        assertEquals("merhaba dünya|ünïcödé", redis.get("greeting"));
    }

    @Test
    void getReturnsNullForMissingKey() {
        assertNull(redis.get("missing"));
    }

    @Test
    void setAppliesTtl() {
        redis.set("short-lived", "x", Duration.ofSeconds(10));

        long pttl = redis.pttl("short-lived");
        assertTrue(pttl > 9_000 && pttl <= 10_000, "pttl was " + pttl);
    }

    @Test
    void setNxOnlySucceedsOnce() {
        assertTrue(redis.setNx("once", "first", Duration.ofSeconds(10)));
        assertFalse(redis.setNx("once", "second", Duration.ofSeconds(10)));
        assertEquals("first", redis.get("once"));
    }

    @Test
    void delReportsRemovedKeys() {
        redis.set("to-delete", "x", Duration.ofSeconds(10));

        assertEquals(1, redis.del("to-delete"));
        assertEquals(0, redis.del("to-delete"));
    }

    @Test
    void evalRunsScriptsAndFallsBackWhenScriptIsNotCached() {
        RedisScript script = new RedisScript("return {KEYS[1], ARGV[1], tonumber(ARGV[2]) + 1}");

        Object reply = redis.eval(script, List.of("k"), List.of("v", "41"));

        assertEquals(List.of("k", "v", 42L), reply);
    }

    @Test
    void redisErrorsSurfaceAsExceptionsAndKeepTheConnectionUsable() {
        redis.set("not-a-number", "abc", Duration.ofSeconds(10));

        RedisException error = assertThrows(RedisException.class, () -> redis.execute("INCR", "not-a-number"));

        assertTrue(error.getMessage().startsWith("ERR"), error.getMessage());
        assertEquals("PONG", redis.ping());
    }
}
