package dev.nostalgiacraft.monitor.metric;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class ItemFlow<K> {
    private static final double TICKS_PER_SECOND = 20;
    private static final long UNKNOWN = -1;
    private static final int WINDOW = 5;

    public record Rates(double in, double out) {
    }

    private record Reading(long gained, long lost, long ticks) {
    }

    private final Deque<Reading> readings = new ArrayDeque<>();

    @Nullable
    private Map<K, Long> previous;
    private long gained;
    private long lost;
    private long sinceTick = UNKNOWN;

    public void observe(Map<K, Long> counts, long tick) {
        if (sinceTick == UNKNOWN) {
            sinceTick = tick;
        }
        if (previous != null) {
            counts.forEach((kind, count) -> change(count - previous.getOrDefault(kind, 0L)));
            previous.forEach((kind, count) -> change(counts.containsKey(kind) ? 0 : -count));
        }
        previous = Map.copyOf(counts);
    }

    public Rates takeRates(long tick) {
        if (sinceTick != UNKNOWN && tick > sinceTick) {
            readings.addLast(new Reading(gained, lost, tick - sinceTick));
            if (readings.size() > WINDOW) {
                readings.removeFirst();
            }
        }
        gained = 0;
        lost = 0;
        sinceTick = tick;
        double seconds = readings.stream().mapToLong(Reading::ticks).sum() / TICKS_PER_SECOND;
        if (seconds <= 0) {
            return new Rates(0, 0);
        }
        return new Rates(readings.stream().mapToLong(Reading::gained).sum() / seconds, readings.stream().mapToLong(Reading::lost).sum() / seconds);
    }

    public void reset() {
        previous = null;
        readings.clear();
        gained = 0;
        lost = 0;
        sinceTick = UNKNOWN;
    }

    private void change(long delta) {
        if (delta > 0) {
            gained += delta;
        } else {
            lost -= delta;
        }
    }
}
