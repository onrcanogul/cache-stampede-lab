package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.cache.strategy.NaiveCacheAside;
import com.onurcanogul.stampede.cache.write.CacheWriter;
import com.onurcanogul.stampede.cache.write.DeleteOnWriteProductWriter;
import com.onurcanogul.stampede.cache.write.ProductWriter;
import com.onurcanogul.stampede.cache.write.VersionedCacheWriter;
import com.onurcanogul.stampede.cache.write.VersionedProductWriter;
import com.onurcanogul.stampede.db.GatedProductRepository;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.report.Table;

import java.time.Duration;

/**
 * A slow reader loads the old row, a writer updates the row and invalidates
 * the key, then the reader writes the old row back. Played deterministically
 * with latches instead of hoping for the right timing.
 */
public final class InvalidationRaceScenario implements Scenario {

    public enum Mode {
        DELETE_ON_WRITE("Delete on write"),
        VERSIONED_WRITE("Versioned write");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Outcome(Mode mode, long databasePrice, long cachedPrice) {

        public boolean stale() {
            return databasePrice != cachedPrice;
        }
    }

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long UPDATED_PRICE = 150;

    @Override
    public String id() {
        return "invalidation";
    }

    @Override
    public String title() {
        return "Invalidation race: reader loads v1, writer stores v2 and invalidates, reader writes v1 back";
    }

    @Override
    public void run(RedisClient redis) {
        Table table = new Table("", "Price in database", "Price in cache", "Stale?");
        for (Mode mode : Mode.values()) {
            Outcome outcome = play(redis, mode);
            table.row(mode.label(), outcome.databasePrice(), outcome.cachedPrice(),
                    outcome.stale() ? "yes, until the TTL ends" : "no");
        }
        table.print(System.out);
    }

    public Outcome play(RedisClient redis, Mode mode) {
        redis.flushDb();
        SimulatedDatabase database = new SimulatedDatabase(Duration.ofMillis(1));
        Product original = Catalog.hotProduct();
        database.seed(original);

        ProductRepository writerRepository = database.connectionPool(1);
        GatedProductRepository readerRepository = new GatedProductRepository(database.connectionPool(1));
        CacheWriter cacheWriter = mode == Mode.VERSIONED_WRITE ? new VersionedCacheWriter(redis) : CacheWriter.plain(redis);
        ProductCache reader = new NaiveCacheAside(redis, readerRepository, TtlPolicy.fixed(TTL), cacheWriter);
        ProductWriter writer = mode == Mode.VERSIONED_WRITE
                ? new VersionedProductWriter(writerRepository, cacheWriter, TtlPolicy.fixed(TTL))
                : new DeleteOnWriteProductWriter(writerRepository, redis);

        Thread slowReader = Thread.ofVirtual().start(() -> reader.get(original.id()));
        readerRepository.awaitRowRead();
        writer.update(original.withPrice(UPDATED_PRICE));
        readerRepository.resumeReader();
        join(slowReader);

        long cachedPrice = Product.decode(redis.get(CacheKeys.product(original.id()))).price();
        return new Outcome(mode, database.peek(original.id()).price(), cachedPrice);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the reader", e);
        }
    }
}
