package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.metric.Sample;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Channel {
    public static final int MAX_SUMS = 8;
    private static final String NEXT_ID = "next_id";
    private static final String SENSORS = "sensors";
    private static final String SUMS = "sums";
    private static final String CENTRAL = "central";
    private static final String ALARMS = "alarms";
    private static final int FEED_GRACE_TICKS = 1;

    private final ChannelKey key;
    private final Map<BlockPos, SensorEntry> sensors = new LinkedHashMap<>();
    private final List<SensorEntry> sums = new ArrayList<>();
    private final Set<BlockPos> panels = new HashSet<>();
    private final AlarmBoard alarms = new AlarmBoard();
    private int nextId = 1;
    private BlockPos central;
    private long lastFedTick = Long.MIN_VALUE;
    private boolean wasPowered;
    private long lastPowerNoticeTick = Long.MIN_VALUE;
    private Set<UUID> observers = Set.of();
    private boolean staleLayout = true;
    private boolean syncedPower;

    Channel(ChannelKey key) {
        this.key = key;
    }

    public ChannelKey key() {
        return key;
    }

    public Collection<SensorEntry> sensors() {
        return sensors.values();
    }

    public List<SensorEntry> sums() {
        return sums;
    }

    public List<SensorEntry> entries() {
        List<SensorEntry> all = new ArrayList<>(sensors.values());
        all.addAll(sums);
        return all;
    }

    @Nullable
    public SensorEntry sensorAt(BlockPos pos) {
        return sensors.get(pos);
    }

    @Nullable
    public SensorEntry sensor(int id) {
        for (SensorEntry entry : sensors.values()) {
            if (entry.id() == id) {
                return entry;
            }
        }
        for (SensorEntry entry : sums) {
            if (entry.id() == id) {
                return entry;
            }
        }
        return null;
    }

    public boolean idle() {
        return observers.isEmpty() && alarms.rules().isEmpty();
    }

    public Set<BlockPos> panels() {
        return panels;
    }

    public Set<UUID> observers() {
        return observers;
    }

    public boolean powered(long tick) {
        return lastFedTick != Long.MIN_VALUE && tick - lastFedTick <= FEED_GRACE_TICKS;
    }

    public void feed(long tick) {
        lastFedTick = tick;
    }

    @Nullable
    public BlockPos central() {
        return central;
    }

    public List<SumSpec> sumSpecs() {
        return sums.stream().map(sum -> new SumSpec(sum.rawName(), sum.metricId(), sum.aggregation())).toList();
    }

    public void replaceSums(List<SumSpec> specs) {
        if (specs.equals(sumSpecs())) {
            return;
        }
        List<SensorEntry> kept = new ArrayList<>();
        for (SumSpec spec : specs.subList(0, Math.min(specs.size(), MAX_SUMS))) {
            SensorEntry existing = sums.stream().filter(sum -> !kept.contains(sum) && sum.metricId().equals(spec.metric())
                    && sum.rawName().equals(spec.name())).findFirst().orElse(null);
            SensorEntry sum = existing == null ? new SensorEntry(this, nextId++, null) : existing;
            sum.describeSum(spec);
            kept.add(sum);
        }
        sums.clear();
        sums.addAll(kept);
        touch();
    }

    public AlarmBoard alarms() {
        return alarms;
    }

    List<AlarmBoard.Notice> evaluateAlarms(long tick, double hysteresis, long cooldownTicks) {
        List<AlarmBoard.Notice> notices = alarms.evaluate(this, tick, hysteresis, cooldownTicks);
        sensors.values().forEach(entry -> entry.setAlarmLevel(alarms.level(entry.id())));
        sums.forEach(entry -> entry.setAlarmLevel(alarms.level(entry.id())));
        return notices;
    }

    void sampleSums() {
        for (SensorEntry sum : sums) {
            sum.record(sum.aggregation().combine(readings(sum.metricId())), 1);
        }
    }

    private List<Sample> readings(String metric) {
        List<Sample> readings = new ArrayList<>();
        for (SensorEntry entry : sensors.values()) {
            if (entry.metricId().equals(metric) && !Double.isNaN(entry.value())) {
                readings.add(new Sample(entry.value(), entry.capacity()));
            }
        }
        return readings;
    }

    void setObservers(Set<UUID> watching) {
        observers = watching;
    }

    void touch() {
        staleLayout = true;
    }

    boolean takeStaleLayout() {
        boolean stale = staleLayout;
        staleLayout = false;
        return stale;
    }

    boolean takePowerChange(boolean powered) {
        boolean changed = powered != syncedPower;
        syncedPower = powered;
        return changed;
    }

    void setCentral(@Nullable BlockPos pos) {
        central = pos == null ? null : pos.immutable();
    }

    boolean powerLost(long tick, long noticeCooldownTicks) {
        boolean powered = powered(tick);
        boolean lost = wasPowered && !powered;
        wasPowered = powered;
        if (!lost || lastPowerNoticeTick != Long.MIN_VALUE && tick - lastPowerNoticeTick < noticeCooldownTicks) {
            return false;
        }
        lastPowerNoticeTick = tick;
        return true;
    }

    SensorEntry add(BlockPos pos) {
        touch();
        return sensors.computeIfAbsent(pos.immutable(), at -> new SensorEntry(this, nextId++, at));
    }

    void remove(BlockPos pos) {
        sensors.remove(pos);
        touch();
    }

    boolean unused() {
        return sensors.isEmpty() && central == null && panels.isEmpty();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        key.save(tag);
        tag.putInt(NEXT_ID, nextId);
        if (central != null) {
            tag.putLong(CENTRAL, central.asLong());
        }
        tag.put(SENSORS, saved(sensors.values()));
        tag.put(SUMS, saved(sums));
        tag.put(ALARMS, alarms.toTag());
        return tag;
    }

    private static ListTag saved(Collection<SensorEntry> entries) {
        ListTag list = new ListTag();
        entries.forEach(entry -> list.add(entry.save()));
        return list;
    }

    static Channel load(CompoundTag tag) {
        Channel channel = new Channel(ChannelKey.load(tag));
        channel.nextId = tag.getInt(NEXT_ID);
        channel.central = tag.contains(CENTRAL) ? BlockPos.of(tag.getLong(CENTRAL)) : null;
        for (Tag element : tag.getList(SENSORS, Tag.TAG_COMPOUND)) {
            SensorEntry entry = SensorEntry.load(channel, (CompoundTag) element);
            channel.sensors.put(entry.pos(), entry);
        }
        for (Tag element : tag.getList(SUMS, Tag.TAG_COMPOUND)) {
            channel.sums.add(SensorEntry.load(channel, (CompoundTag) element));
        }
        channel.alarms.load(tag.getCompound(ALARMS));
        return channel;
    }
}
