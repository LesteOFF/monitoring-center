package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.alarm.AlarmRule;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Comparison;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Severity;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.data.Aggregation;
import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.CentralConfig;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.SumSpec;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.panel.ValueFormat;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class CentralScreen extends WorkshopScreen {
    private final BlockPos pos;
    private final List<SumSpec> sums;
    private final List<AlarmRule> rules;
    private final List<ClientChannels.Sensor> sensors = new ArrayList<>();
    private String channelText;
    private Severity redstone;
    private int tab;
    private int sumPage;
    private int rulePage;
    private boolean sumEditor;
    private boolean ruleEditor;
    private int editingSum = -1;
    private int editingRule = -1;
    private String sumText = "";
    private String sumMetric = Metrics.direct().getFirst().id();
    private Aggregation sumAggregation = Aggregation.SUM;
    private int sensorId;
    private Kind derived = Kind.VALUE;
    private Comparison comparison = Comparison.BELOW;
    private Severity severity = Severity.WARNING;
    private String thresholdText = "";
    private String messageText = "";
    private String holdText = "";
    private Period period = Period.FIVE_MINUTES;

    private final ChannelKey key;
    private final boolean unlinked;

    public CentralScreen(BlockPos pos, ChannelKey key, CompoundTag config) {
        super(text("central"), text("central.help"));
        this.pos = pos;
        this.key = key;
        this.unlinked = CentralConfig.isUnlinked(config);
        channelText = key.name();
        sums = new ArrayList<>(SumSpec.fromTag(config.getList(CentralConfig.SUMS, Tag.TAG_COMPOUND)));
        rules = new ArrayList<>(AlarmBoard.rulesOf(config));
        redstone = AlarmBoard.redstoneOf(config);
        ClientChannels.View view = ClientChannels.get(key);
        if (view != null) sensors.addAll(view.sensors());
        if (!sensors.isEmpty()) sensorId = sensors.getFirst().id();
    }

    @Override
    protected void init() {
        super.init();
        ClientChannels.View view = ClientChannels.get(key);
        if (status.isEmpty() && unlinked) {
            status = text("central.unlinked", key.name()).getString();
        } else if (status.isEmpty() && view != null && !view.powered()) {
            status = text("central.no_power").getString();
        }
        tabs(new String[]{"tab.overview", "tab.sums", "tab.alarms"}, tab, selected -> { tab = selected; status = ""; rebuildWidgets(); });
        if (tab == 0) overview();
        else if (tab == 1 && sumEditor) editSum();
        else if (tab == 2 && ruleEditor) editRule();
        else listEntries();
    }

    private void overview() {
        int x = layout.x(), y = layout.y(), w = layout.contentWidth();
        field(text("channel"), x, y, w, channelText, ChannelKey.MAX_NAME_LENGTH, value -> channelText = value);
        paragraph(text("channel.help"), x, y + 36, w);
        List<Component> levels = new ArrayList<>();
        levels.add(text("redstone.off"));
        for (Severity value : Severity.values()) {
            levels.add(option("severity", value));
        }
        segments(text("label.redstone"), levels, redstone == null ? 0 : redstone.ordinal() + 1, x, y + 56, w,
                selected -> redstone = selected == 0 ? null : Severity.values()[selected - 1]);
        paragraph(text("redstone.help"), x, y + 92, w);
        footer(this::confirm);
    }

    private void listEntries() {
        boolean alarms = tab == 2;
        int count = alarms ? rules.size() : sums.size();
        int page = layout.clampPage(alarms ? rulePage : sumPage, count);
        if (alarms) rulePage = page; else sumPage = page;
        int x = layout.x(), y = layout.y(), w = layout.contentWidth();
        var add = button(text(alarms ? "alarm.add" : "sum.add"), x, y, 112, () -> beginEntry(-1));
        add.active = count < (alarms ? AlarmBoard.MAX_RULES : Channel.MAX_SUMS);
        label(text("entries", count, alarms ? AlarmBoard.MAX_RULES : Channel.MAX_SUMS), x + 120, y + 6, w - 120);
        if (count == 0) {
            note(text(alarms ? "alarms.empty" : "sums.empty"), x, y + 32, w);
            paragraph(text(alarms ? "alarms.help" : "sums.help"), x, y + 48, w);
        }
        for (int index = page * layout.listCapacity(); index < Math.min(count, (page + 1) * layout.listCapacity()); index++) {
            int selected = index;
            Component line = alarms ? Component.literal((index + 1) + ". " + rules.get(index).message())
                    : Component.literal((index + 1) + ". " + sums.get(index).name()).append(" · ").append(metricName(sums.get(index).metric()));
            button(line, x, y + 26 + (index % layout.listCapacity()) * 24, w, false, true, () -> beginEntry(selected))
                    .setTooltip(Tooltip.create(text("edit.entry").append(": ").append(line)));
        }
        pages(page, count, selected -> { if (alarms) rulePage = selected; else sumPage = selected; rebuildWidgets(); });
        footer(this::confirm);
    }

    private void beginEntry(int index) {
        status = "";
        if (tab == 1) {
            editingSum = index;
            if (index >= 0) { sumText = sums.get(index).name(); sumMetric = sums.get(index).metric(); sumAggregation = sums.get(index).aggregation(); }
            else { sumText = ""; sumMetric = Metrics.direct().getFirst().id(); sumAggregation = Aggregation.SUM; }
            sumEditor = true;
        } else {
            editingRule = index;
            if (index >= 0) {
                AlarmRule rule = rules.get(index);
                sensorId = rule.sensorId(); derived = rule.derived(); comparison = rule.comparison(); severity = rule.severity();
                thresholdText = Double.toString(rule.threshold()); messageText = rule.message();
                holdText = rule.holdSeconds() == 0 ? "" : Integer.toString(rule.holdSeconds()); period = rule.period();
            }
            else {
                sensorId = sensors.isEmpty() ? 0 : sensors.getFirst().id();
                derived = Kind.VALUE; comparison = Comparison.BELOW; severity = Severity.WARNING;
                thresholdText = ""; messageText = ""; holdText = ""; period = Period.FIVE_MINUTES;
            }
            ruleEditor = true;
        }
        rebuildWidgets();
    }

    private void editSum() {
        int x = layout.x(), y = layout.y(), w = layout.contentWidth(), half = (w - 6) / 2;
        field(text("sum.name"), x, y, w, sumText, 32, value -> sumText = value);
        choice(text("label.aggregation"), option("aggregation", sumAggregation), x + half + 6, y + 40, w - half - 6,
                () -> chooseEnum("aggregation", Aggregation.values(), value -> sumAggregation = value));
        choice(text("sum.measure"), metricName(sumMetric), x, y + 40, half, () -> {
            List<ChoiceScreen.Entry<String>> entries = Metrics.direct().stream()
                    .map(metric -> new ChoiceScreen.Entry<>(metric.id(), Component.translatable(metric.nameKey()))).toList();
            choose(text("sum.measure"), entries, value -> sumMetric = value);
        });
        paragraph(text("sums.help"), x, y + 80, w);
        if (editingSum >= 0) button(text("remove"), x, y + 98, 92, () -> { sums.remove(editingSum); sumEditor = false; rebuildWidgets(); });
        editorFooter(false);
    }

    private void editRule() {
        int x = layout.x(), y = layout.y(), w = layout.contentWidth(), small = (w - 12) / 3;
        boolean missing = comparison == Comparison.MISSING;
        boolean historic = !missing && derived.ordinal() >= Kind.MINIMUM.ordinal();
        int column = historic ? small : (w - 6) / 2;
        choice(text("label.sensor"), sensorName(sensorId), x, y, column, () -> {
            List<ChoiceScreen.Entry<Integer>> entries = sensors.stream().map(sensor -> new ChoiceScreen.Entry<>(sensor.id(), Component.literal(sensor.name()))).toList();
            choose(text("label.sensor"), entries, value -> sensorId = value);
        });
        choice(text("label.derived"), option("derived", derived), x + column + 6, y, column, () -> chooseEnum("derived", Kind.values(), value -> derived = value));
        if (historic) choice(text("label.period"), option("period", period), x + 2 * (small + 6), y, w - 2 * (small + 6),
                () -> chooseEnum("period", Period.values(), value -> period = value));
        choice(text("alarm.when"), option("comparison", comparison), x, y + 40, small, () -> chooseEnum("comparison", Comparison.values(), value -> comparison = value));
        ClientChannels.Sensor current = sensors.stream().filter(sensor -> sensor.id() == sensorId).findFirst().orElse(null);
        String unit = ValueFormat.unit(derived, current == null ? "" : current.unit());
        Component limitLabel = unit.isBlank() ? text("threshold") : text("threshold.unit", unit);
        if (!missing) field(limitLabel, x + small + 6, y + 40, small, thresholdText, 32, value -> thresholdText = value);
        choice(text("label.severity"), option("severity", severity), x + 2 * (small + 6), y + 40, w - 2 * (small + 6),
                () -> chooseEnum("severity", Severity.values(), value -> severity = value));
        int removeWidth = editingRule >= 0 ? 70 : 0;
        int holdWidth = 56;
        int messageWidth = w - holdWidth - 6 - removeWidth - (removeWidth > 0 ? 6 : 0);
        field(text("message.optional"), x, y + 80, messageWidth, messageText, AlarmRule.MAX_MESSAGE_LENGTH, value -> messageText = value);
        field(text("label.hold"), x + messageWidth + 6, y + 80, holdWidth, holdText, 4, value -> holdText = value);
        if (editingRule >= 0) button(text("remove"), x + w - removeWidth, y + 92, removeWidth,
                () -> { rules.remove(editingRule); ruleEditor = false; rebuildWidgets(); });
        editorFooter(true);
    }

    private void editorFooter(boolean alarms) {
        int x = layout.x(), w = layout.contentWidth(), half = (w - 6) / 2;
        button(text("back"), x, layout.footerY(), half, () -> { if (alarms) ruleEditor = false; else sumEditor = false; status = ""; rebuildWidgets(); });
        button(text("apply.entry"), x + half + 6, layout.footerY(), w - half - 6, true, false, alarms ? this::applyRule : this::applySum);
    }

    private void applySum() {
        if (sumText.isBlank()) { status = text("error.sum_name").getString(); return; }
        SumSpec wanted = new SumSpec(sumText.strip(), sumMetric, sumAggregation);
        if (editingSum >= 0) sums.set(editingSum, wanted);
        else if (sums.size() < Channel.MAX_SUMS) sums.add(wanted);
        sumText = ""; sumEditor = false; status = ""; rebuildWidgets();
    }

    private void applyRule() {
        boolean missing = comparison == Comparison.MISSING;
        Double limit = missing ? Double.valueOf(0) : GuiDrafts.finiteNumber(thresholdText);
        Double hold = holdText.isBlank() ? Double.valueOf(0) : GuiDrafts.finiteNumber(holdText);
        if (limit == null || hold == null) { status = text("error.number").getString(); return; }
        ClientChannels.Sensor sensor = sensors.stream().filter(value -> value.id() == sensorId).findFirst().orElse(null);
        if (sensor == null && editingRule < 0) { status = text("error.sensor").getString(); return; }
        String message = messageText.strip();
        if (message.isEmpty()) {
            String reading = (ValueFormat.derived(derived, limit) + " " + ValueFormat.unit(derived, sensor == null ? "" : sensor.unit())).strip();
            message = sensorName(sensorId).getString() + (missing ? ": " + option("comparison", comparison).getString()
                    : (comparison == Comparison.BELOW ? " < " : " > ") + reading);
        }
        AlarmRule wanted = new AlarmRule(editingRule >= 0 ? rules.get(editingRule).id() : 0, sensorId, derived, comparison, limit, severity, message,
                (int) Math.clamp(Math.round(hold), 0, AlarmRule.MAX_HOLD_SECONDS), period);
        if (editingRule >= 0) rules.set(editingRule, wanted);
        else if (rules.size() < AlarmBoard.MAX_RULES) rules.add(wanted);
        thresholdText = ""; messageText = ""; holdText = ""; ruleEditor = false; status = ""; rebuildWidgets();
    }

    private Component sensorName(int id) {
        return sensors.stream().filter(sensor -> sensor.id() == id).findFirst().<Component>map(sensor -> Component.literal(sensor.name()))
                .orElse(id == 0 ? text("choose.sensor") : text("sensor.unavailable", id));
    }

    private static Component metricName(String id) { return Component.translatable("metric.monitoringcenter." + id); }

    private void confirm() {
        if (sumEditor || ruleEditor) {
            tab = sumEditor ? 1 : 2;
            status = text("error.apply_first").getString();
            rebuildWidgets();
            return;
        }
        PacketDistributor.sendToServer(new Payloads.ConfigureCentral(pos, channelText, buildConfig()));
        onClose();
    }

    CompoundTag buildConfig() {
        CompoundTag sent = AlarmBoard.tagOf(rules, redstone);
        sent.put(CentralConfig.SUMS, SumSpec.toTag(sums));
        return sent;
    }
}
