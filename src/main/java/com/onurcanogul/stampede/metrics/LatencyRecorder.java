package com.onurcanogul.stampede.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class LatencyRecorder {

    private final ConcurrentLinkedQueue<Long> samplesNanos = new ConcurrentLinkedQueue<>();

    public void record(long nanos) {
        samplesNanos.add(nanos);
    }

    public void measure(Runnable action) {
        long started = System.nanoTime();
        try {
            action.run();
        } finally {
            record(System.nanoTime() - started);
        }
    }

    public int count() {
        return samplesNanos.size();
    }

    public Duration percentile(double percentile) {
        long[] sorted = sortedSnapshot();
        if (sorted.length == 0) {
            return Duration.ZERO;
        }
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return Duration.ofNanos(sorted[Math.clamp(rank, 0, sorted.length - 1)]);
    }

    public Duration max() {
        return Duration.ofNanos(samplesNanos.stream().mapToLong(Long::longValue).max().orElse(0L));
    }

    public long countSlowerThan(Duration threshold) {
        long thresholdNanos = threshold.toNanos();
        return samplesNanos.stream().filter(nanos -> nanos > thresholdNanos).count();
    }

    private long[] sortedSnapshot() {
        long[] values = samplesNanos.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(values);
        return values;
    }
}
