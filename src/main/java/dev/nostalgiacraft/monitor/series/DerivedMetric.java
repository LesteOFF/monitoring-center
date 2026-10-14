package dev.nostalgiacraft.monitor.series;

public final class DerivedMetric {
    public static final int RATE_WINDOW_SECONDS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int PERCENT = 100;

    public enum Kind {
        VALUE, PERCENT, RATE_TICK, RATE_SECOND, RATE_MINUTE, FORECAST, MINIMUM, MAXIMUM, AVERAGE
    }

    public enum Period {
        FIVE_MINUTES, SIX_HOURS, SEVEN_DAYS
    }

    private DerivedMetric() {
    }

    public static double compute(Kind kind, Period period, Series series, double value, double capacity) {
        return switch (kind) {
            case VALUE -> value;
            case PERCENT -> capacity > 0 ? value / capacity * PERCENT : Double.NaN;
            case RATE_TICK -> rate(series.seconds(), RATE_WINDOW_SECONDS) / TICKS_PER_SECOND;
            case RATE_SECOND -> rate(series.seconds(), RATE_WINDOW_SECONDS);
            case RATE_MINUTE -> rate(series.seconds(), RATE_WINDOW_SECONDS) * SECONDS_PER_MINUTE;
            case FORECAST -> forecastSeconds(value, capacity, rate(series.seconds(), RATE_WINDOW_SECONDS));
            case MINIMUM -> minimum(buffer(series, period, Series.Stat.MIN));
            case MAXIMUM -> maximum(buffer(series, period, Series.Stat.MAX));
            case AVERAGE -> average(buffer(series, period, Series.Stat.AVG));
        };
    }

    public static RingBuffer buffer(Series series, Period period, Series.Stat stat) {
        return switch (period) {
            case FIVE_MINUTES -> series.seconds();
            case SIX_HOURS -> series.minutes(stat);
            case SEVEN_DAYS -> series.hours(stat);
        };
    }

    public static double rate(RingBuffer seconds, int windowSeconds) {
        if (seconds.size() <= windowSeconds) {
            return Double.NaN;
        }
        float current = seconds.last();
        float before = seconds.get(seconds.size() - 1 - windowSeconds);
        return ((double) current - before) / windowSeconds;
    }

    public static double forecastSeconds(double value, double capacity, double ratePerSecond) {
        if (Double.isNaN(value) || Double.isNaN(ratePerSecond) || ratePerSecond == 0) {
            return Double.NaN;
        }
        if (ratePerSecond < 0) {
            return value / -ratePerSecond;
        }
        return capacity > 0 ? (capacity - value) / ratePerSecond : Double.NaN;
    }

    public static double minimum(RingBuffer buffer) {
        double lowest = Double.NaN;
        for (int index = 0; index < buffer.size(); index++) {
            float sample = buffer.get(index);
            if (!Float.isNaN(sample) && !(sample >= lowest)) {
                lowest = sample;
            }
        }
        return lowest;
    }

    public static double maximum(RingBuffer buffer) {
        double highest = Double.NaN;
        for (int index = 0; index < buffer.size(); index++) {
            float sample = buffer.get(index);
            if (!Float.isNaN(sample) && !(sample <= highest)) {
                highest = sample;
            }
        }
        return highest;
    }

    public static double average(RingBuffer buffer) {
        double sum = 0;
        int valid = 0;
        for (int index = 0; index < buffer.size(); index++) {
            float sample = buffer.get(index);
            if (!Float.isNaN(sample)) {
                sum += sample;
                valid++;
            }
        }
        return valid == 0 ? Double.NaN : sum / valid;
    }
}
