package dev.nostalgiacraft.monitor.panel;

import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record PanelConfig(Mode mode, Theme theme, Period period, int scale, List<Row> rows, String title) {
    public static final PanelConfig DEFAULT = new PanelConfig(Mode.LIST, Theme.AMBER, Period.FIVE_MINUTES, 1, List.of());
    public static final int MAX_ROWS = 16;
    public static final int MAX_TITLE_LENGTH = 24;
    public static final int GRAPH_SERIES = 4;
    public static final int NO_COLOR = -1;
    private static final int MAX_ICON_LENGTH = 96;
    private static final String MODE = "mode";
    private static final String THEME = "theme";
    private static final String PERIOD = "period";
    private static final String SCALE = "scale";
    private static final String ROWS = "rows";
    private static final String SENSOR = "sensor";
    private static final String DERIVED = "derived";
    private static final String ICON = "icon";
    private static final String CHART = "chart";
    private static final String COLOR = "color";
    private static final String TITLE = "title";

    public enum Mode {
        LIST, CARDS, GRAPH, HIGHLIGHT, ALARMS, GAUGE
    }

    public enum Theme {
        AMBER(0xFFFFB000), CYAN(0xFF40E0FF), WHITE(0xFFF0F0F0), GREEN(0xFF4ADE80), LIME(0xFFB6F23C), YELLOW(0xFFFFE14D),
        PINK(0xFFFF8FC7), PURPLE(0xFFB388FF), BLUE(0xFF5B9DFF);

        private final int color;

        Theme(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    public enum Chart {
        SPARKLINE, BAR, NONE
    }

    public record Row(int sensorId, Kind derived, String icon, Chart chart, int color) {
        public static Row of(int sensorId) {
            return new Row(sensorId, Kind.VALUE, "", Chart.SPARKLINE, NO_COLOR);
        }

        public Row withColor(int newColor) {
            return new Row(sensorId, derived, icon, chart, newColor);
        }

        public Row withSensor(int newSensorId) {
            return new Row(newSensorId, derived, icon, chart, color);
        }

        public Row withIcon(String newIcon) {
            return new Row(sensorId, derived, newIcon, chart, color);
        }

        public Row withChart(Chart newChart) {
            return new Row(sensorId, derived, icon, newChart, color);
        }

        public Row withDerived(Kind newDerived) {
            return new Row(sensorId, newDerived, icon, chart, color);
        }

        boolean usesSeries() {
            return chart == Chart.SPARKLINE || derived != Kind.VALUE && derived != Kind.PERCENT;
        }
    }

    public PanelConfig {
        scale = Math.clamp(scale, 1, PanelLayout.MAX_SCALE);
        rows = List.copyOf(rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows);
        title = title.strip().length() > MAX_TITLE_LENGTH ? title.strip().substring(0, MAX_TITLE_LENGTH) : title.strip();
    }

    public PanelConfig(Mode mode, Theme theme, Period period, int scale, List<Row> rows) {
        this(mode, theme, period, scale, rows, "");
    }

    public PanelConfig withRows(List<Row> newRows) {
        return new PanelConfig(mode, theme, period, scale, newRows, title);
    }

    public boolean usesSeries() {
        return mode == Mode.GRAPH || rows.isEmpty() || rows.stream().anyMatch(Row::usesSeries);
    }

    public boolean usesHistory() {
        return period != Period.FIVE_MINUTES
                && (mode == Mode.GRAPH || rows.stream().anyMatch(row -> row.derived().ordinal() >= Kind.MINIMUM.ordinal()));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(MODE, mode.name());
        tag.putString(THEME, theme.name());
        tag.putString(PERIOD, period.name());
        tag.putInt(SCALE, scale);
        tag.putString(TITLE, title);
        ListTag list = new ListTag();
        for (Row row : rows) {
            CompoundTag rowTag = new CompoundTag();
            rowTag.putInt(SENSOR, row.sensorId());
            rowTag.putString(DERIVED, row.derived().name());
            rowTag.putString(ICON, row.icon());
            rowTag.putString(CHART, row.chart().name());
            rowTag.putInt(COLOR, row.color());
            list.add(rowTag);
        }
        tag.put(ROWS, list);
        return tag;
    }

    public static PanelConfig fromTag(CompoundTag tag) {
        List<Row> rows = new ArrayList<>();
        ListTag list = tag.getList(ROWS, Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(list.size(), MAX_ROWS); index++) {
            CompoundTag rowTag = list.getCompound(index);
            String icon = rowTag.getString(ICON);
            rows.add(new Row(rowTag.getInt(SENSOR), constant(Kind.class, rowTag.getString(DERIVED), Kind.VALUE),
                    icon.length() > MAX_ICON_LENGTH ? "" : icon, constant(Chart.class, rowTag.getString(CHART), Chart.SPARKLINE),
                    rowTag.contains(COLOR) ? rowTag.getInt(COLOR) : NO_COLOR));
        }
        return new PanelConfig(constant(Mode.class, tag.getString(MODE), Mode.LIST), constant(Theme.class, tag.getString(THEME), Theme.AMBER),
                constant(Period.class, tag.getString(PERIOD), Period.FIVE_MINUTES), tag.getInt(SCALE), rows, tag.getString(TITLE));
    }

    public static <E extends Enum<E>> E constant(Class<E> type, String name, E fallback) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return fallback;
    }
}
