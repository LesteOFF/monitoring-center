package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.metric.Sample;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public enum Aggregation {
    SUM("sigma"), AVERAGE("flat"), MINIMUM("down"), MAXIMUM("up");

    private static final Comparator<Sample> BY_VALUE = Comparator.comparingDouble(Sample::value);
    private final String icon;

    Aggregation(String icon) {
        this.icon = icon;
    }

    public String icon() {
        return icon;
    }

    @Nullable
    public Sample combine(List<Sample> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        return switch (this) {
            case SUM -> total(samples, 1);
            case AVERAGE -> total(samples, samples.size());
            case MINIMUM -> Collections.min(samples, BY_VALUE);
            case MAXIMUM -> Collections.max(samples, BY_VALUE);
        };
    }

    private static Sample total(List<Sample> samples, int divisor) {
        double value = 0;
        double capacity = 0;
        for (Sample sample : samples) {
            value += sample.value();
            capacity += sample.capacity();
        }
        return new Sample(value / divisor, capacity / divisor);
    }
}
