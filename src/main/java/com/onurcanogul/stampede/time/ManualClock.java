package com.onurcanogul.stampede.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

public final class ManualClock extends Clock {

    private final AtomicLong millis;
    private final ZoneId zone;

    private ManualClock(long millis, ZoneId zone) {
        this.millis = new AtomicLong(millis);
        this.zone = zone;
    }

    public static ManualClock startingNow() {
        return new ManualClock(System.currentTimeMillis(), ZoneOffset.UTC);
    }

    public static ManualClock startingAt(Instant instant) {
        return new ManualClock(instant.toEpochMilli(), ZoneOffset.UTC);
    }

    public void advance(Duration duration) {
        millis.addAndGet(duration.toMillis());
    }

    @Override
    public long millis() {
        return millis.get();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis.get());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ManualClock(millis.get(), zone);
    }
}
