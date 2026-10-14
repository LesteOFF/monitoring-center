package dev.nostalgiacraft.monitor.alarm;

import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;

public record AlarmRule(int id, int sensorId, Kind derived, Comparison comparison, double threshold, Severity severity, String message,
                        int holdSeconds, Period period) {
    public static final int MAX_MESSAGE_LENGTH = 48;
    public static final int MAX_HOLD_SECONDS = 3600;
    private static final int MISSING_GRACE_SECONDS = 10;
    private static final int NEAR_MARGINS = 3;

    public enum Comparison {
        BELOW, ABOVE, MISSING
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public AlarmRule {
        message = message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        holdSeconds = Math.clamp(holdSeconds, 0, MAX_HOLD_SECONDS);
    }

    public AlarmRule(int id, int sensorId, Kind derived, Comparison comparison, double threshold, Severity severity, String message) {
        this(id, sensorId, derived, comparison, threshold, severity, message, 0, Period.FIVE_MINUTES);
    }

    public AlarmRule withId(int newId) {
        return new AlarmRule(newId, sensorId, derived, comparison, threshold, severity, message, holdSeconds, period);
    }

    public Kind observed() {
        return comparison == Comparison.MISSING ? Kind.VALUE : derived;
    }

    public int requiredSeconds() {
        return comparison == Comparison.MISSING ? Math.max(holdSeconds, MISSING_GRACE_SECONDS) : holdSeconds;
    }

    public boolean breached(double value) {
        return switch (comparison) {
            case BELOW -> value < threshold;
            case ABOVE -> value > threshold;
            case MISSING -> Double.isNaN(value);
        };
    }

    public boolean recovered(double value, double hysteresis) {
        double margin = margin(hysteresis);
        return switch (comparison) {
            case BELOW -> value >= threshold + margin;
            case ABOVE -> value <= threshold - margin;
            case MISSING -> !Double.isNaN(value);
        };
    }

    public boolean near(double value, double hysteresis) {
        if (comparison == Comparison.MISSING || Double.isNaN(value) || breached(value)) {
            return false;
        }
        double band = margin(hysteresis) * NEAR_MARGINS;
        return comparison == Comparison.BELOW ? value < threshold + band : value > threshold - band;
    }

    private double margin(double hysteresis) {
        return Math.max(Math.abs(threshold), 1d) * hysteresis;
    }
}
