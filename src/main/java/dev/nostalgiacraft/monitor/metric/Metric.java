package dev.nostalgiacraft.monitor.metric;

public record Metric(String id, String unit, String icon, boolean area, boolean filtered, boolean tanked, MetricReader reader) {
    public Metric(String id, String unit, String icon, boolean area, MetricReader reader) {
        this(id, unit, icon, area, false, false, reader);
    }

    Metric withFilter() {
        return new Metric(id, unit, icon, area, true, tanked, reader);
    }

    Metric withTank() {
        return new Metric(id, unit, icon, area, filtered, true, reader);
    }

    public String nameKey() {
        return "metric.monitoringcenter." + id;
    }
}
