package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.cache.lock.RedisLock;
import com.onurcanogul.stampede.cache.strategy.DistributedLockCache;
import com.onurcanogul.stampede.cache.strategy.NaiveCacheAside;
import com.onurcanogul.stampede.cache.strategy.ProbabilisticEarlyRefreshCache;
import com.onurcanogul.stampede.cache.strategy.SingleFlightCache;
import com.onurcanogul.stampede.cache.strategy.StaleWhileRevalidateCache;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.db.SimulatedDatabase;
import com.onurcanogul.stampede.redis.RedisClient;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * N application instances running the same strategy against one Redis and one
 * database. Each instance has its own connection pool and its own in-process
 * state, which is what makes per-JVM strategies visible.
 */
public final class Cluster implements AutoCloseable {

    private static final Duration LOCK_MAX_WAIT = Duration.ofSeconds(2);
    private static final int HARD_TTL_MULTIPLIER = 5;
    private static final double XFETCH_BETA = 1.0;

    public record Settings(int instances, int connectionsPerInstance, Duration ttl, Duration ttlJitter, Clock clock) {

        public TtlPolicy ttlPolicy() {
            return ttlJitter.isZero() ? TtlPolicy.fixed(ttl) : TtlPolicy.jittered(ttl, ttlJitter);
        }

        TtlPolicy hardTtlPolicy() {
            Duration hard = ttl.multipliedBy(HARD_TTL_MULTIPLIER);
            return ttlJitter.isZero() ? TtlPolicy.fixed(hard) : TtlPolicy.jittered(hard, ttlJitter);
        }
    }

    private final Strategy strategy;
    private final Settings settings;
    private final List<ProductCache> instances;
    private final List<ExecutorService> refreshers;

    private Cluster(Strategy strategy, Settings settings, List<ProductCache> instances, List<ExecutorService> refreshers) {
        this.strategy = strategy;
        this.settings = settings;
        this.instances = instances;
        this.refreshers = refreshers;
    }

    public static Cluster of(Strategy strategy, RedisClient redis, SimulatedDatabase database, Settings settings) {
        List<ProductCache> instances = new ArrayList<>(settings.instances());
        List<ExecutorService> refreshers = new ArrayList<>();
        for (int i = 0; i < settings.instances(); i++) {
            ProductRepository repository = database.connectionPool(settings.connectionsPerInstance());
            instances.add(build(strategy, redis, repository, settings, refreshers));
        }
        return new Cluster(strategy, settings, instances, refreshers);
    }

    private static ProductCache build(Strategy strategy, RedisClient redis, ProductRepository repository,
                                      Settings settings, List<ExecutorService> refreshers) {
        TtlPolicy ttl = settings.ttlPolicy();
        return switch (strategy) {
            case NAIVE -> new NaiveCacheAside(redis, repository, ttl);
            case SINGLE_FLIGHT -> new SingleFlightCache(redis, repository, ttl);
            case DISTRIBUTED_LOCK -> new DistributedLockCache(redis, repository, ttl, new RedisLock(redis), LOCK_MAX_WAIT);
            case STALE_WHILE_REVALIDATE -> {
                ExecutorService refresher = Executors.newVirtualThreadPerTaskExecutor();
                refreshers.add(refresher);
                yield new StaleWhileRevalidateCache(redis, repository, settings.ttl(), settings.hardTtlPolicy(),
                        new RedisLock(redis), refresher, settings.clock());
            }
            case PROBABILISTIC_EARLY_REFRESH ->
                    new ProbabilisticEarlyRefreshCache(redis, repository, ttl, XFETCH_BETA, settings.clock());
        };
    }

    public Strategy strategy() {
        return strategy;
    }

    public Settings settings() {
        return settings;
    }

    public int size() {
        return instances.size();
    }

    public ProductCache instance(int index) {
        return instances.get(index);
    }

    public ProductCache route(int request) {
        return instances.get(Math.floorMod(request, instances.size()));
    }

    @Override
    public void close() {
        refreshers.forEach(ExecutorService::close);
    }
}
