package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.alarm.AlarmRule;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Comparison;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Severity;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import net.minecraft.nbt.CompoundTag;

public record ActuatorSettings(Source source, int ruleId, AlarmRule condition, boolean inverted) {
    public enum Source {
        RULE, SENSOR
    }

    private static final String SOURCE = "source";
    private static final String RULE = "rule";
    private static final String CONDITION = "condition";
    private static final String INVERTED = "inverted";

    public static final ActuatorSettings DEFAULT = new ActuatorSettings(Source.RULE, 0,
            new AlarmRule(0, 0, Kind.VALUE, Comparison.BELOW, 0, Severity.INFO, ""), false);

    public boolean output(boolean conditionMet) {
        return conditionMet != inverted;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE, source.name());
        tag.putInt(RULE, ruleId);
        tag.put(CONDITION, AlarmBoard.ruleTag(condition));
        tag.putBoolean(INVERTED, inverted);
        return tag;
    }

    public static ActuatorSettings fromTag(CompoundTag tag) {
        if (!tag.contains(SOURCE)) {
            return DEFAULT;
        }
        return new ActuatorSettings(PanelConfig.constant(Source.class, tag.getString(SOURCE), Source.RULE), tag.getInt(RULE),
                AlarmBoard.ruleOf(tag.getCompound(CONDITION)), tag.getBoolean(INVERTED));
    }
}
