package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.metric.Metric;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.series.Series;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public final class SensorEntry implements SensorScheduler.Slot {
    private static final String ID = "id";
    private static final String POS = "pos";
    private static final String NAME = "name";
    private static final String METRIC = "metric";
    private static final String UNIT = "unit";
    private static final String ICON = "icon";
    private static final String AREA = "area";
    private static final String SERIES = "series";
    private static final String AGGREGATION = "aggregation";

    private final Channel channel;
    private final int id;
    private final BlockPos pos;
    private final Series series = new Series();
    private String name = "";
    private String metricId = "";
    private String unit = "";
    private String icon = "";
    private boolean area;
    private Aggregation aggregation = Aggregation.SUM;
    private double value = Double.NaN;
    private double capacity;
    private double sentValue = Double.NaN;
    private double sentCapacity;
    private int alarmLevel;
    private int sentAlarmLevel;

    SensorEntry(Channel channel, int id, @Nullable BlockPos pos) {
        this.channel = channel;
        this.id = id;
        this.pos = pos;
    }

    public void describe(String newName, Metric metric, String newIcon) {
        if (!newName.equals(name) || !metric.id().equals(metricId) || !newIcon.equals(icon)) {
            channel.touch();
        }
        name = newName;
        metricId = metric.id();
        unit = metric.unit();
        area = metric.area();
        icon = newIcon;
    }

    void describeSum(SumSpec spec) {
        Metric metric = Metrics.get(spec.metric());
        name = spec.name();
        metricId = spec.metric();
        unit = metric == null ? "" : metric.unit();
        aggregation = spec.aggregation();
        icon = aggregation.icon();
    }

    Aggregation aggregation() {
        return aggregation;
    }

    boolean takeChange() {
        boolean changed = Double.compare(value, sentValue) != 0 || Double.compare(capacity, sentCapacity) != 0 || alarmLevel != sentAlarmLevel;
        sentValue = value;
        sentCapacity = capacity;
        sentAlarmLevel = alarmLevel;
        return changed;
    }

    void setAlarmLevel(int level) {
        alarmLevel = level;
    }

    public int alarmLevel() {
        return alarmLevel;
    }

    public void record(@Nullable Sample sample, int seconds) {
        value = sample == null ? Double.NaN : sample.value();
        capacity = sample == null ? 0 : sample.capacity();
        for (int second = 0; second < seconds; second++) {
            series.push((float) value);
        }
    }

    public Channel channel() {
        return channel;
    }

    public int id() {
        return id;
    }

    @Nullable
    public BlockPos pos() {
        return pos;
    }

    public String name() {
        return name.isBlank() ? "Sensor " + id : name;
    }

    String rawName() {
        return name;
    }

    public String metricId() {
        return metricId;
    }

    public String unit() {
        return unit;
    }

    public String icon() {
        return icon;
    }

    public Series series() {
        return series;
    }

    public double value() {
        return value;
    }

    public double capacity() {
        return capacity;
    }

    @Override
    public boolean area() {
        return area;
    }

    @Override
    public boolean idle() {
        return channel.idle();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ID, id);
        if (pos != null) {
            tag.putLong(POS, pos.asLong());
        }
        tag.putString(NAME, name);
        tag.putString(METRIC, metricId);
        tag.putString(UNIT, unit);
        tag.putString(ICON, icon);
        tag.putBoolean(AREA, area);
        tag.putString(AGGREGATION, aggregation.name());
        tag.put(SERIES, SeriesNbt.save(series));
        return tag;
    }

    static SensorEntry load(Channel channel, CompoundTag tag) {
        SensorEntry entry = new SensorEntry(channel, tag.getInt(ID), tag.contains(POS) ? BlockPos.of(tag.getLong(POS)) : null);
        entry.name = tag.getString(NAME);
        entry.metricId = tag.getString(METRIC);
        entry.unit = tag.getString(UNIT);
        entry.icon = tag.getString(ICON);
        entry.area = tag.getBoolean(AREA);
        entry.aggregation = PanelConfig.constant(Aggregation.class, tag.getString(AGGREGATION), Aggregation.SUM);
        SeriesNbt.load(entry.series, tag.getCompound(SERIES));
        return entry;
    }
}
