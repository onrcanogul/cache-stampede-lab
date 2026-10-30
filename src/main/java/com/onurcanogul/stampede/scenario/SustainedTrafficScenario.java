package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.metrics.LatencyRecorder;
import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.report.Table;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Constant traffic on the hot key across several TTL cycles. This is the only
 * fair way to measure probabilistic early refresh.
 */
public final class SustainedTrafficScenario implements Scenario {

    public record Settings(int instances, int connectionsPerInstance, Duration queryLatency, int requestsPerSecond,
                           Duration duration, Duration ttl, Duration slowThreshold) {

        public static Settings article() {
            return new Settings(3, 10, Duration.ofMillis(200), 300, Duration.ofSeconds(10), Duration.ofSeconds(2),
                    Duration.ofMillis(50));
        }
    }

    public record Result(Strategy strategy, long databaseQueries, long slowRequests, LatencyRecorder latency) {
    }

    private final Settings settings;

    public SustainedTrafficScenario() {
        this(Settings.article());
    }

    public SustainedTrafficScenario(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "sustained";
    }

    @Override
    public String title() {
        return "Sustained traffic: %d req/s for %d s, %d s TTL, %d ms query".formatted(
                settings.requestsPerSecond(), settings.duration().toSeconds(), settings.ttl().toSeconds(),
                settings.queryLatency().toMillis());
    }

    @Override
    public void run(RedisClient redis) {
        String slowHeader = "Slower than " + Table.millis(settings.slowThreshold());
        Table table = new Table("", "DB queries", slowHeader, "p99", "p99.9");
        for (Strategy strategy : Strategy.values()) {
            System.out.println("  measuring " + strategy.label() + "...");
            Result result = measure(redis, strategy);
            table.row(result.strategy().label(), result.databaseQueries(),
                    result.slowRequests() + " / " + result.latency().count(),
                    result.latency().percentile(99), result.latency().percentile(99.9));
        }
        System.out.println();
        table.print(System.out);
    }

    public Result measure(RedisClient redis, Strategy strategy) {
        redis.flushDb();
        SimulatedDatabase database = new SimulatedDatabase(settings.queryLatency());
        database.seed(Catalog.hotProduct());
        Cluster.Settings clusterSettings = new Cluster.Settings(
                settings.instances(), settings.connectionsPerInstance(), settings.ttl(), Duration.ZERO, Clock.systemUTC());

        LatencyRecorder latency;
        try (Cluster cluster = Cluster.of(strategy, redis, database, clusterSettings)) {
            cluster.instance(0).get(Catalog.HOT_PRODUCT_ID);
            database.stats().reset();
            latency = Workload.constantRate(settings.requestsPerSecond(), settings.duration(),
                    i -> cluster.route(i).get(Catalog.HOT_PRODUCT_ID));
        }
        long slow = latency.countSlowerThan(settings.slowThreshold());
        return new Result(strategy, database.stats().total(), slow, latency);
    }
}
