package dev.nostalgiacraft.monitor.client.render;

import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.client.render.PanelCanvas.Align;
import dev.nostalgiacraft.monitor.config.ClientConfig;
import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Rect;
import dev.nostalgiacraft.monitor.panel.ValueFormat;
import dev.nostalgiacraft.monitor.series.DerivedMetric;
import dev.nostalgiacraft.monitor.series.RingBuffer;
import dev.nostalgiacraft.monitor.series.Series;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PanelPainter {
    static final long PULSE_MILLIS = 500;
    private static final int DIM = 0xFF55606B;
    private static final int GRID = 0x28FFFFFF;
    private static final int[] GRAPH_COLORS = {0, 0xFFFF6B6B, 0xFF6BFF95, 0xFFB388FF};
    private static final int[] SEVERITY_COLORS = {0xFF40E0FF, 0xFFFF8C1A, 0xFFFF3B30};
    private static final int ALARM = 0xFFFF3B30;
    private static final int ALARM_DIM = 0xFF8A1F1A;
    private static final int NEAR = 0xFFFF8C1A;
    private static final double TREND_THRESHOLD = 0.0005;
    private static final int TICKS_PER_HOUR = 1000;
    private static final int HOURS_PER_DAY = 24;
    private static final int DAWN_HOUR = 6;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int GRID_LINES = 2;
    private static final int LEGEND_GAP = 6;
    private static final int SWATCH = 6;
    private static final int SWATCH_GAP = 2;
    private static final int GRAPH_LINE = 2;
    private static final int TEXT_SCALE = 1;
    private static final int CARD_SCALE = 2;
    private static final int HIGHLIGHT_SCALE = 24;

    record Scene(PanelConfig config, PanelLayout.Size size, int page, List<String> links, @Nullable ChannelKey channel, int minute) {
        static Scene of(ScreenBlockEntity screen) {
            return new Scene(screen.config(), screen.size(), screen.page(), screen.linkLines(), screen.channel(), clockMinute(screen.getLevel()));
        }

        String link(int index) {
            return index < links.size() ? links.get(index) : "";
        }
    }

    private record Trace(ClientChannels.Sensor sensor, int color, RingBuffer samples) {
    }

    private interface Cell {
        void paint(PanelLayout.Card card, PanelConfig.Row row, ClientChannels.Sensor sensor);
    }

    private record Range(float low, float high) {
        Range include(Range other) {
            return new Range(Math.min(low, other.low), Math.max(high, other.high));
        }
    }

    private final PanelCanvas canvas = new PanelCanvas();
    private final Scene scene;
    private final PanelConfig config;
    private final PanelLayout.Size size;
    private final int theme;
    private final boolean plain = ClientConfig.PERFORMANCE_MODE.get();
    private final long now = System.currentTimeMillis();

    private PanelPainter(Scene scene) {
        this.scene = scene;
        this.config = scene.config();
        this.size = scene.size();
        this.theme = config.theme().color();
    }

    static PanelMesh paint(Scene scene, @Nullable ClientChannels.View view) {
        PanelPainter painter = new PanelPainter(scene);
        painter.draw(view);
        return painter.canvas.mesh();
    }

    static int clockMinute(@Nullable Level level) {
        long dayTime = level == null ? 0 : level.getDayTime() % (HOURS_PER_DAY * TICKS_PER_HOUR);
        return (int) (dayTime * MINUTES_PER_HOUR / TICKS_PER_HOUR);
    }

    private void draw(@Nullable ClientChannels.View view) {
        if (view == null) {
            message("panel.monitoringcenter.no_channel");
        } else if (!view.powered()) {
            message("panel.monitoringcenter.no_power");
        } else {
            content(view);
        }
    }

    private void message(String key) {
        canvas.textIn(I18n.get(key), size.bounds(), Align.CENTER, TEXT_SCALE, DIM);
    }

    private void content(ClientChannels.View view) {
        List<PanelConfig.Row> rows = config.rows().isEmpty() ? automaticRows(view) : config.rows();
        requestHistory(view, rows);
        switch (config.mode()) {
            case LIST -> list(view, rows);
            case CARDS -> grid(view, rows, this::plainCard);
            case GAUGE -> grid(view, rows, (card, row, sensor) -> gauge(view, card, row, sensor));
            case GRAPH -> graph(view, rows);
            case HIGHLIGHT -> highlight(view, rows);
            case ALARMS -> alarms(view);
        }
    }

    private static List<PanelConfig.Row> automaticRows(ClientChannels.View view) {
        List<PanelConfig.Row> rows = new ArrayList<>();
        for (ClientChannels.Sensor sensor : view.sensors()) {
            rows.add(PanelConfig.Row.of(sensor.id()));
        }
        return rows;
    }

    private void requestHistory(ClientChannels.View view, List<PanelConfig.Row> rows) {
        if (!config.usesHistory() || scene.channel() == null) {
            return;
        }
        for (PanelConfig.Row row : rows) {
            ClientChannels.Sensor sensor = view.sensor(row.sensorId());
            if (sensor != null) {
                ClientChannels.requestHistory(scene.channel(), sensor);
            }
        }
    }

    private int firstItem(int items, int pageSize, Rect footer) {
        int pages = PanelLayout.pageCount(items, pageSize);
        int page = Math.min(scene.page(), pages - 1);
        if (!footer.empty()) {
            canvas.textIn("< " + (page + 1) + "/" + pages + " >", footer, Align.CENTER, TEXT_SCALE, DIM);
        }
        return page * pageSize;
    }

    private void list(ClientChannels.View view, List<PanelConfig.Row> rows) {
        int items = Math.max(rows.size(), scene.links().size());
        PanelLayout.Layout layout = PanelLayout.list(size, items);
        header(layout.header());
        int first = firstItem(items, layout.rows().size(), layout.footer());
        for (int line = 0; line < layout.rows().size(); line++) {
            int index = first + line;
            PanelLayout.Row place = layout.rows().get(line);
            ClientChannels.Sensor sensor = index < rows.size() ? view.sensor(rows.get(index).sensorId()) : null;
            if (!scene.link(index).isEmpty()) {
                canvas.textIn(scene.link(index), place.bounds(), Align.LEFT, TEXT_SCALE, theme);
            } else if (sensor != null) {
                row(place, rows.get(index), sensor);
            }
        }
    }

    private void header(Rect header) {
        if (header.empty()) {
            return;
        }
        String clock = clock();
        int clockWidth = (int) Math.ceil(canvas.width(clock, TEXT_SCALE));
        Rect name = new Rect(header.x(), header.y(), header.width() - clockWidth - LEGEND_GAP, header.height());
        canvas.textIn(title().toUpperCase(), name, Align.LEFT, TEXT_SCALE, theme);
        canvas.textIn(clock, header, Align.RIGHT, TEXT_SCALE, theme);
        canvas.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), DIM);
    }

    private String title() {
        if (!config.title().isEmpty()) {
            return config.title();
        }
        return scene.channel() == null ? "" : scene.channel().name();
    }

    private String clock() {
        int hour = (scene.minute() / MINUTES_PER_HOUR + DAWN_HOUR) % HOURS_PER_DAY;
        return "%02d:%02d".formatted(hour, scene.minute() % MINUTES_PER_HOUR);
    }

    private void row(PanelLayout.Row place, PanelConfig.Row row, ClientChannels.Sensor sensor) {
        int color = color(row, sensor);
        PanelIcons.draw(canvas, icon(row, sensor), place.icon(), color);
        if (!place.name().empty()) {
            canvas.textIn(sensor.name(), place.name(), Align.LEFT, TEXT_SCALE, color);
        }
        value(place.value(), row, sensor, color, TEXT_SCALE, Align.RIGHT);
        chart(place.chart(), row.chart(), sensor, color);
        if (!place.trend().empty()) {
            PanelIcons.drawGlyph(canvas, trend(sensor), place.trend(), color);
        }
    }

    private void alarms(ClientChannels.View view) {
        List<Payloads.AlarmLine> lines = view.alarms();
        PanelLayout.Layout layout = PanelLayout.list(size, lines.size());
        header(layout.header());
        if (lines.isEmpty()) {
            canvas.textIn(I18n.get("panel.monitoringcenter.no_alarms"), layout.rows().getFirst().bounds(), Align.CENTER, TEXT_SCALE, DIM);
        }
        int first = firstItem(lines.size(), layout.rows().size(), layout.footer());
        for (int line = 0; line < layout.rows().size() && first + line < lines.size(); line++) {
            alarmLine(layout.rows().get(line), lines.get(first + line));
        }
    }

    private void alarmLine(PanelLayout.Row place, Payloads.AlarmLine line) {
        int color = line.active() ? SEVERITY_COLORS[Math.clamp(line.severity(), 0, SEVERITY_COLORS.length - 1)] : DIM;
        PanelIcons.drawGlyph(canvas, line.active() ? "warning" : "bell", place.icon(), color);
        int left = place.icon().right() + PanelLayout.LINE - PanelLayout.GLYPH;
        Rect text = new Rect(left, place.bounds().y(), place.bounds().right() - left, place.bounds().height());
        canvas.textIn(line.message(), text, Align.LEFT, TEXT_SCALE, color);
    }

    private void grid(ClientChannels.View view, List<PanelConfig.Row> rows, Cell painted) {
        PanelLayout.Cards layout = PanelLayout.cards(size, rows.size());
        List<PanelLayout.Card> cards = layout.cards();
        int first = firstItem(rows.size(), cards.size(), layout.footer());
        for (int cell = 0; cell < cards.size() && first + cell < rows.size(); cell++) {
            PanelConfig.Row row = rows.get(first + cell);
            ClientChannels.Sensor sensor = view.sensor(row.sensorId());
            if (sensor != null) {
                Rect bounds = cards.get(cell).bounds();
                painted.paint(cards.get(cell), row, sensor);
                canvas.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), GRID);
                canvas.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), GRID);
            }
        }
    }

    private void plainCard(PanelLayout.Card card, PanelConfig.Row row, ClientChannels.Sensor sensor) {
        card(card, row, sensor, CARD_SCALE, Align.LEFT);
    }

    private void gauge(ClientChannels.View view, PanelLayout.Card card, PanelConfig.Row row, ClientChannels.Sensor sensor) {
        int color = color(row, sensor);
        Rect value = card.value();
        Rect reading = new Rect(value.x(), value.y(), value.width(), PanelLayout.LINE);
        Rect meter = new Rect(value.x(), reading.bottom(), value.width(), value.height() - PanelLayout.LINE);
        PanelIcons.draw(canvas, icon(row, sensor), card.icon(), color);
        canvas.textIn(sensor.name(), card.name(), Align.LEFT, TEXT_SCALE, color);
        canvas.textIn(gaugeReading(sensor), reading, Align.LEFT, TEXT_SCALE, color);
        if (sensor.sum()) {
            canvas.stack(meter, shares(view, sensor), new int[]{color, GRAPH_COLORS[1], GRAPH_COLORS[2], GRAPH_COLORS[3]});
        } else {
            canvas.bar(meter, fullness(sensor), color);
        }
    }

    private static String gaugeReading(ClientChannels.Sensor sensor) {
        if (sensor.capacity() > 0) {
            return ValueFormat.percent(sensor.value(), sensor.capacity());
        }
        return (ValueFormat.number(sensor.value()) + " " + sensor.unit()).strip();
    }

    private static List<Double> shares(ClientChannels.View view, ClientChannels.Sensor sum) {
        double full = sum.capacity() > 0 ? sum.capacity() : sum.value();
        List<Double> shares = new ArrayList<>();
        for (ClientChannels.Sensor part : view.sensors()) {
            if (full > 0 && !part.sum() && part.metric().equals(sum.metric()) && part.value() > 0) {
                shares.add(part.value() / full);
            }
        }
        return shares;
    }

    private static double fullness(ClientChannels.Sensor sensor) {
        double full = sensor.capacity() > 0 ? sensor.capacity() : DerivedMetric.maximum(sensor.series().seconds());
        return full > 0 ? sensor.value() / full : Double.NaN;
    }

    private void highlight(ClientChannels.View view, List<PanelConfig.Row> rows) {
        ClientChannels.Sensor sensor = rows.isEmpty() ? null : view.sensor(rows.getFirst().sensorId());
        if (sensor != null) {
            card(PanelLayout.highlight(size), rows.getFirst(), sensor, HIGHLIGHT_SCALE, Align.CENTER);
        }
    }

    private void card(PanelLayout.Card card, PanelConfig.Row row, ClientChannels.Sensor sensor, int largestScale, Align align) {
        int color = color(row, sensor);
        if (!card.icon().empty()) {
            PanelIcons.draw(canvas, icon(row, sensor), card.icon(), color);
        }
        if (!card.name().empty()) {
            canvas.textIn(sensor.name(), card.name(), align, TEXT_SCALE, color);
        }
        value(card.value(), row, sensor, color, largestScale, align);
    }

    private void graph(ClientChannels.View view, List<PanelConfig.Row> rows) {
        List<Trace> traces = traces(view, rows);
        PanelLayout.Graph layout = PanelLayout.graph(size, traces.size());
        Rect plot = layout.plot();
        for (int step = 0; step <= GRID_LINES; step++) {
            int y = plot.y() + (plot.height() - 1) * step / GRID_LINES;
            canvas.fill(plot.x(), y, plot.right(), y + 1, GRID);
        }
        timeline(layout.timeline());
        Map<String, Range> ranges = ranges(traces);
        for (int index = 0; index < traces.size(); index++) {
            Trace trace = traces.get(index);
            Range range = ranges.get(trace.sensor().unit());
            canvas.line(plot, trace.samples(), trace.color(), GRAPH_LINE, range.low(), range.high());
            if (index < layout.legend().size()) {
                legend(layout.legend().get(index), trace);
            }
        }
        if (!traces.isEmpty()) {
            axis(layout.axis(), ranges.get(traces.getFirst().sensor().unit()), traces.getFirst().color());
        }
    }

    private List<Trace> traces(ClientChannels.View view, List<PanelConfig.Row> rows) {
        List<Trace> traces = new ArrayList<>();
        for (int index = 0; index < Math.min(rows.size(), PanelConfig.GRAPH_SERIES); index++) {
            ClientChannels.Sensor sensor = view.sensor(rows.get(index).sensorId());
            if (sensor != null) {
                int color = index == 0 ? color(rows.get(index), sensor) : GRAPH_COLORS[index];
                traces.add(new Trace(sensor, color, DerivedMetric.buffer(sensor.series(), config.period(), Series.Stat.AVG)));
            }
        }
        return traces;
    }

    private static Map<String, Range> ranges(List<Trace> traces) {
        Map<String, Range> ranges = new HashMap<>();
        for (Trace trace : traces) {
            double lowest = DerivedMetric.minimum(trace.samples());
            double highest = DerivedMetric.maximum(trace.samples());
            float low = Double.isNaN(lowest) ? 0f : (float) Math.min(0, lowest);
            float high = Double.isNaN(highest) || highest <= low ? low + 1f : (float) highest;
            ranges.merge(trace.sensor().unit(), new Range(low, high), Range::include);
        }
        return ranges;
    }

    private void timeline(Rect timeline) {
        if (timeline.empty()) {
            return;
        }
        String oldest = switch (config.period()) {
            case FIVE_MINUTES -> "-5 min";
            case SIX_HOURS -> "-6 h";
            case SEVEN_DAYS -> "-7 d";
        };
        canvas.textIn(oldest, timeline, Align.LEFT, TEXT_SCALE, DIM);
        canvas.textIn(I18n.get("panel.monitoringcenter.now"), timeline, Align.RIGHT, TEXT_SCALE, DIM);
    }

    private void axis(Rect axis, Range range, int color) {
        if (axis.empty()) {
            return;
        }
        int middle = axis.y() + (axis.height() - PanelLayout.LINE) / 2;
        canvas.textIn(ValueFormat.number(range.high()), new Rect(axis.x(), axis.y(), axis.width(), PanelLayout.LINE), Align.RIGHT, TEXT_SCALE, color);
        canvas.textIn(ValueFormat.number((range.low() + range.high()) / 2), new Rect(axis.x(), middle, axis.width(), PanelLayout.LINE),
                Align.RIGHT, TEXT_SCALE, DIM);
        canvas.textIn(ValueFormat.number(range.low()), new Rect(axis.x(), axis.bottom() - PanelLayout.LINE, axis.width(), PanelLayout.LINE),
                Align.RIGHT, TEXT_SCALE, DIM);
    }

    private void legend(Rect slot, Trace trace) {
        ClientChannels.Sensor sensor = trace.sensor();
        String label = (sensor.name() + " " + ValueFormat.number(sensor.value()) + " " + sensor.unit()).strip();
        int top = slot.y() + (slot.height() - SWATCH) / 2;
        int textLeft = slot.x() + SWATCH + SWATCH_GAP;
        canvas.fill(slot.x(), top, slot.x() + SWATCH, top + SWATCH, trace.color());
        canvas.textIn(label, new Rect(textLeft, slot.y(), slot.right() - textLeft, slot.height()), Align.LEFT, TEXT_SCALE, trace.color());
    }

    private void value(Rect area, PanelConfig.Row row, ClientChannels.Sensor sensor, int color, int largestScale, Align align) {
        double computed = DerivedMetric.compute(row.derived(), config.period(), sensor.series(), sensor.value(), sensor.capacity());
        boolean clock = row.derived() == DerivedMetric.Kind.VALUE && sensor.metric().equals(Metrics.TIME);
        String number = clock ? ValueFormat.clock(computed) : ValueFormat.derived(row.derived(), computed);
        String unit = clock ? "" : ValueFormat.unit(row.derived(), sensor.unit());
        String complete = unit.isEmpty() ? number : number + " " + unit;
        int scale = canvas.fittingScale(number, area, largestScale);
        canvas.textIn(canvas.width(complete, scale) <= area.width() ? complete : number, area, align, scale, color);
    }

    private void chart(Rect area, PanelConfig.Chart chart, ClientChannels.Sensor sensor, int color) {
        if (area.empty() || chart == PanelConfig.Chart.NONE) {
            return;
        }
        if (chart == PanelConfig.Chart.BAR || plain) {
            canvas.bar(area, fullness(sensor), color);
        } else {
            canvas.line(area, sensor.series().seconds(), color);
        }
    }

    private static String trend(ClientChannels.Sensor sensor) {
        double rate = DerivedMetric.rate(sensor.series().seconds(), DerivedMetric.RATE_WINDOW_SECONDS);
        if (Double.isNaN(rate)) {
            return "flat";
        }
        double tolerance = Math.max(Math.abs(sensor.value()), 1d) * TREND_THRESHOLD;
        return rate > tolerance ? "up" : rate < -tolerance ? "down" : "flat";
    }

    private int color(PanelConfig.Row row, ClientChannels.Sensor sensor) {
        if (sensor.alarmLevel() == AlarmBoard.LEVEL_ALARM) {
            return plain || now / PULSE_MILLIS % 2 == 0 ? ALARM : ALARM_DIM;
        }
        if (sensor.alarmLevel() == AlarmBoard.LEVEL_NEAR) {
            return NEAR;
        }
        return row.color() == PanelConfig.NO_COLOR ? theme : row.color();
    }

    private static String icon(PanelConfig.Row row, ClientChannels.Sensor sensor) {
        return row.icon().isEmpty() ? sensor.icon() : row.icon();
    }
}
