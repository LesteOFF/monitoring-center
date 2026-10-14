package dev.nostalgiacraft.monitor.metric;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Metrics {
    public static final String REDSTONE = "redstone";
    public static final String ITEMS = "items";
    public static final String TIME = "time";
    public static final String ITEM_FLOW = "item_flow";
    public static final String ITEM_OUT = "item_out";
    private static final Map<String, Metric> BY_ID = new LinkedHashMap<>();

    private Metrics() {
    }

    public static void registerCore() {
        direct("energy", "FE", "bolt", UniversalReaders::energy);
        direct("fluid", "mB", "drop", UniversalReaders::fluid);
        direct(ITEMS, "", "box", UniversalReaders::items);
        filtered(ITEMS);
        direct(ITEM_FLOW, "/s", "down", UniversalReaders::itemIn);
        filtered(ITEM_FLOW);
        direct(ITEM_OUT, "/s", "up", UniversalReaders::itemOut);
        filtered(ITEM_OUT);
        direct(REDSTONE, "", "redstone", UniversalReaders::redstone);
        tanked("fluid");
        direct("fluid_total", "mB", "drop", UniversalReaders::fluidTotal);
        filtered("fluid_total");
        WorldReaders.register();
    }

    public static boolean tracksItemFlow(String id) {
        return ITEM_FLOW.equals(id) || ITEM_OUT.equals(id);
    }

    public static void trackItemFlow(SensorBlockEntity sensor) {
        UniversalReaders.trackItemFlow(sensor);
    }

    public static void direct(String id, String unit, String icon, MetricReader reader) {
        BY_ID.put(id, new Metric(id, unit, icon, false, reader));
    }

    public static void area(String id, String unit, String icon, MetricReader reader) {
        BY_ID.put(id, new Metric(id, unit, icon, true, reader));
    }

    public static void filtered(String id) {
        BY_ID.computeIfPresent(id, (key, metric) -> metric.withFilter());
    }

    public static boolean usesFilter(String id) {
        Metric metric = BY_ID.get(id);
        return metric != null && metric.filtered();
    }

    public static void tanked(String id) {
        BY_ID.computeIfPresent(id, (key, metric) -> metric.withTank());
    }

    public static boolean usesTank(String id) {
        Metric metric = BY_ID.get(id);
        return metric != null && metric.tanked();
    }

    @Nullable
    public static Metric get(String id) {
        return BY_ID.get(id);
    }

    public static List<Metric> direct() {
        return BY_ID.values().stream().filter(metric -> !metric.area()).toList();
    }

    public static List<Metric> applicable(ServerLevel level, SensorBlockEntity sensor) {
        List<Metric> metrics = new ArrayList<>();
        for (Metric metric : BY_ID.values()) {
            if (metric.area() || metric.reader().read(level, sensor) != null) {
                metrics.add(metric);
            }
        }
        return metrics;
    }

    public static Metric detect(ServerLevel level, SensorBlockEntity sensor) {
        if (UniversalReaders.items(level, sensor) != null) {
            return BY_ID.get(UniversalReaders.energy(level, sensor) != null ? ITEM_OUT : ITEMS);
        }
        for (Metric metric : BY_ID.values()) {
            if (!metric.area() && !metric.id().equals(REDSTONE) && metric.reader().read(level, sensor) != null) {
                return metric;
            }
        }
        return BY_ID.get(REDSTONE);
    }
}
