package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.metrics.LatencyRecorder;
import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.report.Table;
import com.onurcanogul.stampede.time.ManualClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The hot key expires and a burst of requests arrives at the same instant.
 */
public final class ExpiryBurstScenario implements Scenario {

    public record Settings(int instances, int connectionsPerInstance, Duration queryLatency, int requests, Duration ttl) {

        public static Settings article() {
            return new Settings(3, 10, Duration.ofMillis(200), 600, Duration.ofSeconds(30));
        }
    }

    public record Result(Strategy strategy, long databaseQueries, int maxConcurrentQueries, LatencyRecorder latency) {
    }

    private static final List<Strategy> STRATEGIES = List.of(
            Strategy.NAIVE, Strategy.SINGLE_FLIGHT, Strategy.DISTRIBUTED_LOCK, Strategy.STALE_WHILE_REVALIDATE);

    private final Settings settings;

    public ExpiryBurstScenario() {
        this(Settings.article());
    }

    public ExpiryBurstScenario(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "burst";
    }

    @Override
    public String title() {
        return "Expiry burst: %d instances x %d connections, %d ms query, %d simultaneous requests".formatted(
                settings.instances(), settings.connectionsPerInstance(), settings.queryLatency().toMillis(), settings.requests());
    }

    @Override
    public void run(RedisClient redis) {
        Table table = new Table("", "DB queries", "Max concurrent", "p50", "p99");
        for (Result result : measure(redis)) {
            table.row(result.strategy().label(), result.databaseQueries(), result.maxConcurrentQueries(),
                    result.latency().percentile(50), result.latency().percentile(99));
        }
        table.print(System.out);
    }

    public List<Result> measure(RedisClient redis) {
        List<Result> results = new ArrayList<>();
        for (Strategy strategy : STRATEGIES) {
            results.add(measure(redis, strategy));
        }
        return results;
    }

    public Result measure(RedisClient redis, Strategy strategy) {
        redis.flushDb();
        SimulatedDatabase database = new SimulatedDatabase(settings.queryLatency());
        database.seed(Catalog.hotProduct());
        ManualClock clock = ManualClock.startingNow();
        Cluster.Settings clusterSettings = new Cluster.Settings(
                settings.instances(), settings.connectionsPerInstance(), settings.ttl(), Duration.ZERO, clock);

        LatencyRecorder latency;
        try (Cluster cluster = Cluster.of(strategy, redis, database, clusterSettings)) {
            cluster.instance(0).get(Catalog.HOT_PRODUCT_ID);
            expire(strategy, redis, clock);
            database.stats().reset();
            latency = Workload.burst(settings.requests(), i -> cluster.route(i).get(Catalog.HOT_PRODUCT_ID));
        }
        return new Result(strategy, database.stats().total(), database.stats().maxInFlight(), latency);
    }

    private void expire(Strategy strategy, RedisClient redis, ManualClock clock) {
        if (strategy == Strategy.STALE_WHILE_REVALIDATE) {
            clock.advance(settings.ttl().plusMillis(1));
        } else {
            redis.del(CacheKeys.product(Catalog.HOT_PRODUCT_ID));
        }
    }
}
