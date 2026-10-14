package dev.nostalgiacraft.monitor.panel;

import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;

public final class ValueFormat {
    public static final String MISSING = "--";
    private static final String[] SUFFIXES = {"", " k", " M", " G", " T"};
    private static final int STEP = 1000;
    private static final int PERCENT = 100;
    private static final int WHOLE_PERCENT_FROM = 10;
    private static final int MINUTE = 60;
    private static final int HOUR = 3600;
    private static final int DAY = 86400;

    private ValueFormat() {
    }

    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return MISSING;
        }
        double magnitude = Math.abs(value);
        int tier = 0;
        while (roundedToTenth(magnitude) >= STEP && tier < SUFFIXES.length - 1) {
            magnitude /= STEP;
            tier++;
        }
        double rounded = roundedToTenth(magnitude);
        String digits = rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded).replace('.', ',');
        return (value < 0 && rounded != 0 ? "-" : "") + digits + SUFFIXES[tier];
    }

    public static String percent(double value, double capacity) {
        if (Double.isNaN(value) || capacity <= 0) {
            return MISSING;
        }
        return percentage(value / capacity * PERCENT);
    }

    public static String percentage(double percentage) {
        if (Double.isNaN(percentage)) {
            return MISSING;
        }
        double tenths = roundedToTenth(Math.abs(percentage));
        if (percentage > 0 && tenths == 0) {
            return "<0,1%";
        }
        boolean whole = tenths >= WHOLE_PERCENT_FROM || tenths == Math.rint(tenths);
        String digits = whole ? Long.toString(Math.round(Math.abs(percentage))) : Double.toString(tenths).replace('.', ',');
        return (percentage < 0 ? "-" : "") + digits + "%";
    }

    public static String duration(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            return MISSING;
        }
        if (seconds < MINUTE) {
            return Math.round(seconds) + " s";
        }
        if (seconds < HOUR) {
            return Math.round(seconds / MINUTE) + " min";
        }
        return seconds < DAY ? Math.round(seconds / HOUR) + " h" : Math.round(seconds / DAY) + " d";
    }

    public static String clock(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0) {
            return MISSING;
        }
        int minutes = (int) Math.floor(hours * MINUTE);
        return "%02d:%02d".formatted(minutes / MINUTE, minutes % MINUTE);
    }

    public static String derived(Kind kind, double computed) {
        return switch (kind) {
            case FORECAST -> duration(computed);
            case PERCENT -> percentage(computed);
            case RATE_TICK, RATE_SECOND, RATE_MINUTE -> (computed > 0 ? "+" : "") + number(computed);
            default -> number(computed);
        };
    }

    public static String unit(Kind kind, String baseUnit) {
        return switch (kind) {
            case FORECAST, PERCENT -> "";
            case RATE_TICK -> baseUnit + "/t";
            case RATE_SECOND -> baseUnit + "/s";
            case RATE_MINUTE -> baseUnit + "/min";
            default -> baseUnit;
        };
    }

    private static double roundedToTenth(double magnitude) {
        return Math.round(magnitude * 10) / 10.0;
    }
}
