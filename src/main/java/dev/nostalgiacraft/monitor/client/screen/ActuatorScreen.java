package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.alarm.AlarmRule;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Comparison;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Severity;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.data.ActuatorSettings;
import dev.nostalgiacraft.monitor.data.ActuatorSettings.Source;
import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class ActuatorScreen extends WorkshopScreen {
    private static final int INVERTED_OPTION = 1;

    private final BlockPos pos;
    private final ChannelKey key;
    private final List<AlarmRule> rules;
    private final List<ClientChannels.Sensor> sensors = new ArrayList<>();
    private String channelText;
    private Source source;
    private int ruleId;
    private boolean inverted;
    private int sensorId;
    private Kind derived;
    private Comparison comparison;
    private Period period;
    private String thresholdText;
    private String holdText;

    public ActuatorScreen(BlockPos pos, ChannelKey key, CompoundTag settingsTag, CompoundTag rulesTag) {
        super(text("actuator"), text("actuator.help"));
        this.pos = pos;
        this.key = key;
        this.rules = AlarmBoard.rulesOf(rulesTag);
        ClientChannels.View view = ClientChannels.get(key);
        if (view != null) sensors.addAll(view.sensors());
        ActuatorSettings settings = ActuatorSettings.fromTag(settingsTag);
        AlarmRule condition = settings.condition();
        channelText = key.name();
        source = settings.source();
        inverted = settings.inverted();
        ruleId = settings.ruleId() == 0 && !rules.isEmpty() ? rules.getFirst().id() : settings.ruleId();
        sensorId = condition.sensorId() == 0 && !sensors.isEmpty() ? sensors.getFirst().id() : condition.sensorId();
        derived = condition.derived();
        comparison = condition.comparison();
        period = condition.period();
        thresholdText = condition.sensorId() == 0 ? "" : Double.toString(condition.threshold());
        holdText = condition.holdSeconds() == 0 ? "" : Integer.toString(condition.holdSeconds());
    }

    @Override
    protected void init() {
        super.init();
        ClientChannels.View view = ClientChannels.get(key);
        if (status.isEmpty() && view != null && !view.powered()) {
            status = text("central.no_power").getString();
        }
        int x = layout.x(), y = layout.y(), w = layout.contentWidth(), half = (w - 6) / 2;
        field(text("channel"), x, y, w, channelText, ChannelKey.MAX_NAME_LENGTH, value -> channelText = value)
                .setTooltip(Tooltip.create(text("channel.help")));
        segments(text("label.actuator_source"), List.of(option("actuator_source", Source.RULE), option("actuator_source", Source.SENSOR)),
                source.ordinal(), x, y + 38, half, selected -> source = Source.values()[selected]);
        segments(text("label.actuator_output"), List.of(text("actuator_output.met"), text("actuator_output.unmet")),
                inverted ? INVERTED_OPTION : 0, x + half + 6, y + 38, w - half - 6, selected -> inverted = selected == INVERTED_OPTION);
        if (source == Source.RULE) {
            ruleFields(x, y + 76, w);
        } else {
            sensorFields(x, y + 76, w);
        }
        footer(this::confirm);
    }

    private void ruleFields(int x, int y, int w) {
        if (rules.isEmpty()) {
            paragraph(text("actuator.no_rules"), x, y, w);
            return;
        }
        choice(text("label.rule"), ruleName(ruleId), x, y, w, () -> choose(text("label.rule"), rules.stream()
                .map(rule -> new ChoiceScreen.Entry<>(rule.id(), (Component) Component.literal(rule.message()))).toList(), value -> ruleId = value));
        paragraph(text("actuator.rule.help"), x, y + 40, w);
    }

    private void sensorFields(int x, int y, int w) {
        int small = (w - 12) / 3;
        boolean missing = comparison == Comparison.MISSING;
        boolean historic = !missing && derived.ordinal() >= Kind.MINIMUM.ordinal();
        int column = historic ? small : (w - 6) / 2;
        choice(text("label.sensor"), ConditionFields.sensorName(sensors, sensorId), x, y, column,
                () -> choose(text("label.sensor"), ConditionFields.sensorChoices(sensors), value -> sensorId = value));
        choice(text("label.derived"), option("derived", derived), x + column + 6, y, column,
                () -> chooseEnum("derived", Kind.values(), value -> derived = value));
        if (historic) {
            choice(text("label.period"), option("period", period), x + 2 * (small + 6), y, w - 2 * (small + 6),
                    () -> chooseEnum("period", Period.values(), value -> period = value));
        }
        choice(text("alarm.when"), option("comparison", comparison), x, y + 40, small,
                () -> chooseEnum("comparison", Comparison.values(), value -> comparison = value));
        if (!missing) {
            field(ConditionFields.limitLabel(sensors, sensorId, derived), x + small + 6, y + 40, small, thresholdText, 32,
                    value -> thresholdText = value);
        }
        field(text("label.hold"), x + 2 * (small + 6), y + 40, w - 2 * (small + 6), holdText, 4, value -> holdText = value);
    }

    private Component ruleName(int id) {
        return rules.stream().filter(rule -> rule.id() == id).findFirst().<Component>map(rule -> Component.literal(rule.message()))
                .orElse(id == 0 ? text("choose.rule") : text("rule.unavailable", id));
    }

    private void confirm() {
        boolean needsLimit = source == Source.SENSOR && comparison != Comparison.MISSING;
        Double typedLimit = GuiDrafts.finiteNumber(thresholdText);
        Double hold = holdText.isBlank() ? Double.valueOf(0) : GuiDrafts.finiteNumber(holdText);
        if (needsLimit && typedLimit == null || hold == null) {
            status = text("error.number").getString();
            rebuildWidgets();
            return;
        }
        AlarmRule condition = new AlarmRule(0, sensorId, derived, comparison, typedLimit == null ? 0 : typedLimit, Severity.INFO, "",
                (int) Math.clamp(Math.round(hold), 0, AlarmRule.MAX_HOLD_SECONDS), period);
        ActuatorSettings settings = new ActuatorSettings(source, ruleId, condition, inverted);
        PacketDistributor.sendToServer(new Payloads.ConfigureActuator(pos, channelText, settings.toTag()));
        onClose();
    }
}
