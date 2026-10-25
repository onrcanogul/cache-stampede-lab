package com.onurcanogul.stampede.db;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class QueryStats {

    private final AtomicLong total = new AtomicLong();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final ConcurrentLinkedQueue<Long> startOffsetsNanos = new ConcurrentLinkedQueue<>();
    private volatile long originNanos = System.nanoTime();

    void begin() {
        total.incrementAndGet();
        int current = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(current, Math::max);
        startOffsetsNanos.add(System.nanoTime() - originNanos);
    }

    void end() {
        inFlight.decrementAndGet();
    }

    public long total() {
        return total.get();
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    public void reset() {
        total.set(0);
        maxInFlight.set(inFlight.get());
        startOffsetsNanos.clear();
        originNanos = System.nanoTime();
    }

    public int[] histogram(Duration bucket, Duration horizon) {
        long bucketNanos = bucket.toNanos();
        int[] counts = new int[(int) Math.ceil((double) horizon.toNanos() / bucketNanos)];
        for (long offset : startOffsetsNanos) {
            int index = (int) (offset / bucketNanos);
            if (index >= 0 && index < counts.length) {
                counts[index]++;
            }
        }
        return counts;
    }

    public int peak(Duration bucket) {
        long last = startOffsetsNanos.stream().mapToLong(Long::longValue).max().orElse(0L);
        int[] counts = histogram(bucket, Duration.ofNanos(last).plus(bucket));
        int peak = 0;
        for (int count : counts) {
            peak = Math.max(peak, count);
        }
        return peak;
    }
}
