package dev.nostalgiacraft.monitor.client;

import dev.nostalgiacraft.monitor.data.Aggregation;
import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.series.Series;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientChannels {
    private static final int TICKS_PER_SECOND = 20;
    private static final int HISTORY_RETRY_TICKS = 100;
    private static final Map<ChannelKey, View> VIEWS = new HashMap<>();
    private static long ticks;

    public static final class Sensor {
        private final int id;
        private final String name;
        private final String unit;
        private final String icon;
        private final String metric;
        private final Series series = new Series();
        private double value;
        private double capacity;
        private long historyAskedTick = Long.MIN_VALUE;
        private boolean historyReceived;
        private int alarmLevel;

        private Sensor(Payloads.SensorState state) {
            id = state.id();
            name = state.name();
            unit = state.unit();
            icon = state.icon();
            metric = state.metric();
            value = state.value();
            capacity = state.capacity();
            alarmLevel = state.alarm();
            series.seconds().restore(state.seconds());
        }

        public int id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String unit() {
            return unit;
        }

        public String icon() {
            return icon;
        }

        public String metric() {
            return metric;
        }

        public boolean sum() {
            return icon.equals(Aggregation.SUM.icon());
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

        public int alarmLevel() {
            return alarmLevel;
        }
    }

    public static final class View {
        private final Map<Integer, Sensor> sensors = new LinkedHashMap<>();
        private List<Payloads.AlarmLine> alarms = List.of();
        private boolean alarming;
        private boolean powered;
        private int valueVersion;
        private int seriesVersion;

        public Collection<Sensor> sensors() {
            return sensors.values();
        }

        @Nullable
        public Sensor sensor(int id) {
            return sensors.get(id);
        }

        public boolean powered() {
            return powered;
        }

        public List<Payloads.AlarmLine> alarms() {
            return alarms;
        }

        public boolean alarming() {
            return alarming;
        }

        private void refreshAlarming() {
            alarming = sensors.values().stream().anyMatch(sensor -> sensor.alarmLevel == AlarmBoard.LEVEL_ALARM);
        }

        public int valueVersion() {
            return valueVersion;
        }

        public int seriesVersion() {
            return seriesVersion;
        }
    }

    private ClientChannels() {
    }

    @Nullable
    public static View get(@Nullable ChannelKey key) {
        return key == null ? null : VIEWS.get(key);
    }

    public static void snapshot(Payloads.ChannelSnapshot message) {
        View view = VIEWS.computeIfAbsent(message.key(), key -> new View());
        view.sensors.clear();
        message.sensors().forEach(state -> view.sensors.put(state.id(), new Sensor(state)));
        view.powered = message.powered();
        view.alarms = message.alarms();
        view.refreshAlarming();
        view.valueVersion++;
    }

    public static void delta(Payloads.ChannelDelta message) {
        View view = VIEWS.get(message.key());
        if (view == null) {
            return;
        }
        view.powered = message.powered();
        for (Payloads.Reading reading : message.readings()) {
            Sensor sensor = view.sensors.get(reading.id());
            if (sensor != null) {
                sensor.value = reading.value();
                sensor.capacity = reading.capacity();
                sensor.alarmLevel = reading.alarm();
            }
        }
        if (message.alarmsChanged()) {
            view.alarms = message.alarms();
        }
        view.refreshAlarming();
        view.valueVersion++;
    }

    public static void close(ChannelKey key) {
        VIEWS.remove(key);
    }

    public static void history(Payloads.History message) {
        View view = VIEWS.get(message.key());
        Sensor sensor = view == null ? null : view.sensors.get(message.sensorId());
        List<float[]> tiers = message.tiers();
        if (sensor == null || tiers.size() != 2 * Series.Stat.values().length) {
            return;
        }
        for (Series.Stat stat : Series.Stat.values()) {
            sensor.series.minutes(stat).restore(tiers.get(stat.ordinal()));
            sensor.series.hours(stat).restore(tiers.get(Series.Stat.values().length + stat.ordinal()));
        }
        sensor.historyReceived = true;
        view.valueVersion++;
    }

    public static void requestHistory(ChannelKey key, Sensor sensor) {
        boolean waited = sensor.historyAskedTick == Long.MIN_VALUE || ticks - sensor.historyAskedTick >= HISTORY_RETRY_TICKS;
        if (!sensor.historyReceived && waited) {
            sensor.historyAskedTick = ticks;
            PacketDistributor.sendToServer(new Payloads.RequestHistory(key, sensor.id));
        }
    }

    public static void tick() {
        if (++ticks % TICKS_PER_SECOND != 0) {
            return;
        }
        for (View view : VIEWS.values()) {
            for (Sensor sensor : view.sensors.values()) {
                sensor.series.push(view.powered ? (float) sensor.value : Float.NaN);
            }
            view.seriesVersion++;
        }
    }

    public static void clear() {
        VIEWS.clear();
    }
}
