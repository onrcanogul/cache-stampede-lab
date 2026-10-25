package com.onurcanogul.stampede.cache.strategy;

import com.onurcanogul.stampede.RedisTestSupport;
import com.onurcanogul.stampede.cache.ProductCache;
import com.onurcanogul.stampede.domain.Product;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

abstract class StrategyTestSupport extends RedisTestSupport {

    static final long PRODUCT_ID = 42L;
    static final Product PRODUCT = new Product(PRODUCT_ID, 1, 100, "Mechanical keyboard");
    static final Duration QUERY_LATENCY = Duration.ofMillis(50);
    static final Duration TTL = Duration.ofSeconds(30);

    /** Starts every call at the same instant so they all observe the miss. */
    static List<Product> concurrentGets(int calls, IntFunction<ProductCache> cacheForCall) {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Product>> futures = new ArrayList<>(calls);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < calls; i++) {
                ProductCache cache = cacheForCall.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.get(PRODUCT_ID);
                }));
            }
            start.countDown();
        }
        List<Product> results = new ArrayList<>(calls);
        for (Future<Product> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                throw new AssertionError("concurrent get failed", e);
            }
        }
        return results;
    }
}
