package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.metrics.LatencyRecorder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;

public final class Workload {

    private Workload() {
    }

    /** Releases all requests at the same instant. */
    public static LatencyRecorder burst(int requests, IntConsumer request) {
        LatencyRecorder latency = new LatencyRecorder();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(requests);
            for (int i = 0; i < requests; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    latency.measure(() -> request.accept(index));
                    return null;
                }));
            }
            start.countDown();
            awaitAll(futures);
        }
        return latency;
    }

    /** Paces requests at a constant rate; a late request is sent immediately instead of being dropped. */
    public static LatencyRecorder constantRate(int requestsPerSecond, Duration duration, IntConsumer request) {
        LatencyRecorder latency = new LatencyRecorder();
        long total = requestsPerSecond * duration.toMillis() / 1000;
        long intervalNanos = 1_000_000_000L / requestsPerSecond;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>((int) total);
            long start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                long target = start + i * intervalNanos;
                long now;
                while ((now = System.nanoTime()) < target) {
                    LockSupport.parkNanos(target - now);
                }
                int index = i;
                futures.add(executor.submit(() -> latency.measure(() -> request.accept(index))));
            }
            awaitAll(futures);
        }
        return latency;
    }

    private static void awaitAll(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for requests", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("request failed", e.getCause());
            }
        }
    }
}
