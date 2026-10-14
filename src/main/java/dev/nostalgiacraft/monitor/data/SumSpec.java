package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public record SumSpec(String name, String metric, Aggregation aggregation) {
    private static final int MAX_NAME_LENGTH = 32;
    private static final String NAME = "name";
    private static final String METRIC = "metric";
    private static final String AGGREGATION = "aggregation";

    public SumSpec {
        name = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public SumSpec(String name, String metric) {
        this(name, metric, Aggregation.SUM);
    }

    public static ListTag toTag(List<SumSpec> specs) {
        ListTag list = new ListTag();
        for (SumSpec spec : specs) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NAME, spec.name());
            tag.putString(METRIC, spec.metric());
            tag.putString(AGGREGATION, spec.aggregation().name());
            list.add(tag);
        }
        return list;
    }

    public static List<SumSpec> fromTag(ListTag list) {
        List<SumSpec> specs = new ArrayList<>();
        for (int index = 0; index < Math.min(list.size(), Channel.MAX_SUMS); index++) {
            CompoundTag tag = list.getCompound(index);
            if (Metrics.get(tag.getString(METRIC)) != null && !tag.getString(NAME).isBlank()) {
                specs.add(new SumSpec(tag.getString(NAME), tag.getString(METRIC),
                        PanelConfig.constant(Aggregation.class, tag.getString(AGGREGATION), Aggregation.SUM)));
            }
        }
        return specs;
    }
}
