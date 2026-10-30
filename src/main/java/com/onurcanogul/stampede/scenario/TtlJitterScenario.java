package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.QueryStats;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.domain.Product;
import com.onurcanogul.stampede.metrics.LatencyRecorder;
import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.report.Table;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * A warm-up job writes thousands of keys at once. With a fixed TTL they all
 * expire together; jitter spreads the reload.
 */
public final class TtlJitterScenario implements Scenario {

    public record Settings(int instances, int connectionsPerInstance, Duration queryLatency, int keys,
                           int requestsPerSecond, Duration duration, Duration ttl, Duration maxJitter) {

        public static Settings article() {
            return new Settings(3, 10, Duration.ofMillis(20), 2_000, 3_000, Duration.ofSeconds(10),
                    Duration.ofSeconds(3), Duration.ofSeconds(1));
        }
    }

    public record Result(String label, long databaseQueries, int peakPer100Millis, LatencyRecorder latency,
                         int[] queriesPer500Millis) {
    }

    private static final Duration PEAK_BUCKET = Duration.ofMillis(100);
    private static final Duration HISTOGRAM_BUCKET = Duration.ofMillis(500);
    private static final long SEED = 20261031L;

    private final Settings settings;

    public TtlJitterScenario() {
        this(Settings.article());
    }

    public TtlJitterScenario(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "jitter";
    }

    @Override
    public String title() {
        return "TTL jitter: %d keys written at once, then %d req/s on random keys for %d s, %d ms query".formatted(
                settings.keys(), settings.requestsPerSecond(), settings.duration().toSeconds(),
                settings.queryLatency().toMillis());
    }

    @Override
    public void run(RedisClient redis) {
        String fixedLabel = "Fixed " + settings.ttl().toSeconds() + " s";
        String jitterLabel = "%d s + [0, %d s] jitter".formatted(settings.ttl().toSeconds(), settings.maxJitter().toSeconds());

        System.out.println("  measuring fixed TTL...");
        Result fixed = measure(redis, fixedLabel, Duration.ZERO);
        System.out.println("  measuring jittered TTL...");
        Result jittered = measure(redis, jitterLabel, settings.maxJitter());
        System.out.println();

        new Table("TTL", "DB queries", "Peak per 100 ms", "p99", "max")
                .row(fixed.label(), fixed.databaseQueries(), fixed.peakPer100Millis(),
                        fixed.latency().percentile(99), fixed.latency().max())
                .row(jittered.label(), jittered.databaseQueries(), jittered.peakPer100Millis(),
                        jittered.latency().percentile(99), jittered.latency().max())
                .print(System.out);

        System.out.println();
        System.out.println("Database queries per 500 ms:");
        System.out.println("  fixed  " + join(fixed.queriesPer500Millis()));
        System.out.println("  jitter " + join(jittered.queriesPer500Millis()));
    }

    public Result measure(RedisClient redis, String label, Duration jitter) {
        redis.flushDb();
        SimulatedDatabase database = new SimulatedDatabase(settings.queryLatency());
        List<Product> products = Catalog.products(settings.keys());
        database.seed(products);
        Cluster.Settings clusterSettings = new Cluster.Settings(
                settings.instances(), settings.connectionsPerInstance(), settings.ttl(), jitter, Clock.systemUTC());

        int[] requestedIds = randomIds(settings.requestsPerSecond() * (int) settings.duration().toSeconds());
        LatencyRecorder latency;
        try (Cluster cluster = Cluster.of(Strategy.NAIVE, redis, database, clusterSettings)) {
            warmUp(redis, products, clusterSettings.ttlPolicy());
            database.stats().reset();
            latency = Workload.constantRate(settings.requestsPerSecond(), settings.duration(),
                    i -> cluster.route(i).get(requestedIds[i]));
        }

        QueryStats stats = database.stats();
        Duration horizon = settings.duration().plusSeconds(1);
        return new Result(label, stats.total(), stats.peak(PEAK_BUCKET), latency, stats.histogram(HISTOGRAM_BUCKET, horizon));
    }

    private static void warmUp(RedisClient redis, List<Product> products, TtlPolicy ttl) {
        for (Product product : products) {
            redis.set(CacheKeys.product(product.id()), product.encode(), ttl.next());
        }
    }

    private int[] randomIds(int count) {
        Random random = new Random(SEED);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = 1 + random.nextInt(settings.keys());
        }
        return ids;
    }

    private static String join(int[] counts) {
        StringBuilder sb = new StringBuilder();
        for (int count : counts) {
            sb.append(String.format("%4d", count));
        }
        return sb.toString();
    }
}
