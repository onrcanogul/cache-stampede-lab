package com.onurcanogul.stampede.cache.write;

import com.onurcanogul.stampede.RedisTestSupport;
import com.onurcanogul.stampede.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedCacheWriterTest extends RedisTestSupport {

    private static final String KEY = "product:42";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Product V1 = new Product(42, 1, 100, "Mechanical keyboard");
    private static final Product V2 = V1.withPrice(150);
    private static final Product V3 = V2.withPrice(175);

    private VersionedCacheWriter writer;

    @BeforeEach
    void setUp() {
        writer = new VersionedCacheWriter(redis);
    }

    @Test
    void writesWhenTheKeyIsEmpty() {
        assertTrue(writer.write(KEY, V1, TTL));
        assertEquals(V1, Product.decode(redis.get(KEY)));
    }

    @Test
    void refusesToOverwriteANewerVersion() {
        writer.write(KEY, V2, TTL);

        assertFalse(writer.write(KEY, V1, TTL));
        assertEquals(V2, Product.decode(redis.get(KEY)));
    }

    @Test
    void refusesToRewriteTheSameVersion() {
        writer.write(KEY, V2, TTL);

        assertFalse(writer.write(KEY, V2, TTL));
    }

    @Test
    void acceptsANewerVersion() {
        writer.write(KEY, V2, TTL);

        assertTrue(writer.write(KEY, V3, TTL));
        assertEquals(V3, Product.decode(redis.get(KEY)));
    }
}
