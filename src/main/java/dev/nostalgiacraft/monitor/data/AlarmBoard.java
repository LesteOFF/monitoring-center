package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.alarm.AlarmRule;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Comparison;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Severity;
import dev.nostalgiacraft.monitor.alarm.AlarmState;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.series.DerivedMetric;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AlarmBoard {
    public static final int MAX_RULES = 8;
    public static final int LEVEL_NORMAL = 0;
    public static final int LEVEL_NEAR = 1;
    public static final int LEVEL_ALARM = 2;
    public static final String RULES = "rules";
    public static final String REDSTONE = "redstone";
    public static final String REDSTONE_OFF = "OFF";
    private static final int RESOLVED_LINES = 6;
    private static final String NEXT_ID = "next_rule_id";
    private static final String ACTIVE = "active";
    private static final String ACKNOWLEDGED = "acknowledged";
    private static final String RESOLVED = "resolved";
    private static final String HOLD = "hold";
    private static final String PERIOD = "period";
    private static final int SIGNAL_PER_SEVERITY = 5;
    private static final String ID = "id";
    private static final String SENSOR = "sensor";
    private static final String DERIVED = "derived";
    private static final String COMPARISON = "comparison";
    private static final String THRESHOLD = "threshold";
    private static final String SEVERITY = "severity";
    private static final String MESSAGE = "message";

    public record Line(String message, int severity, boolean active) {
    }

    public record Notice(AlarmRule rule, boolean raised, String sensorName, String unit, double value) {
    }

    private final List<AlarmRule> rules = new ArrayList<>();
    private final Map<Integer, AlarmState> states = new HashMap<>();
    private final Map<Integer, Integer> levels = new HashMap<>();
    private final Deque<Line> resolved = new ArrayDeque<>();
    private int nextId = 1;
    private Severity redstone;
    private boolean changed = true;

    public List<AlarmRule> rules() {
        return rules;
    }

    @Nullable
    public Severity redstone() {
        return redstone;
    }

    public void replace(List<AlarmRule> wanted, @Nullable Severity redstoneSeverity) {
        List<AlarmRule> previous = List.copyOf(rules);
        rules.clear();
        levels.clear();
        for (AlarmRule rule : wanted.subList(0, Math.min(wanted.size(), MAX_RULES))) {
            rules.add(previous.contains(rule) && !rules.contains(rule) ? rule : rule.withId(nextId++));
        }
        states.keySet().removeIf(id -> rules.stream().noneMatch(rule -> rule.id() == id));
        rules.forEach(rule -> states.computeIfAbsent(rule.id(), id -> new AlarmState()));
        redstone = redstoneSeverity;
        changed = true;
    }

    public boolean emitting() {
        if (redstone == null) {
            return false;
        }
        for (AlarmRule rule : rules) {
            if (rule.severity().ordinal() >= redstone.ordinal() && states.get(rule.id()).sounding()) {
                return true;
            }
        }
        return false;
    }

    public int signalStrength() {
        int strength = 0;
        for (AlarmRule rule : rules) {
            if (states.get(rule.id()).sounding()) {
                strength = Math.max(strength, (rule.severity().ordinal() + 1) * SIGNAL_PER_SEVERITY);
            }
        }
        return strength;
    }

    public int activeCount() {
        int active = 0;
        for (AlarmRule rule : rules) {
            if (states.get(rule.id()).active()) {
                active++;
            }
        }
        return active;
    }

    public int acknowledge() {
        int silenced = 0;
        for (AlarmRule rule : rules) {
            if (states.get(rule.id()).acknowledge()) {
                silenced++;
            }
        }
        changed |= silenced > 0;
        return silenced;
    }

    public int level(int sensorId) {
        return levels.getOrDefault(sensorId, LEVEL_NORMAL);
    }

    public List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        for (AlarmRule rule : rules) {
            if (states.get(rule.id()).active()) {
                lines.add(new Line(rule.message(), rule.severity().ordinal(), true));
            }
        }
        lines.addAll(resolved);
        return lines;
    }

    boolean takeChange() {
        boolean wasChanged = changed;
        changed = false;
        return wasChanged;
    }

    List<Notice> evaluate(Channel channel, long tick, double hysteresis, long cooldownTicks) {
        List<Notice> notices = new ArrayList<>();
        levels.clear();
        for (AlarmRule rule : rules) {
            SensorEntry entry = channel.sensor(rule.sensorId());
            AlarmState state = states.get(rule.id());
            if (entry != null) {
                evaluate(rule, entry, tick, hysteresis, cooldownTicks, notices);
            } else if (state.active()) {
                state.restore(false);
                changed = true;
            }
        }
        return notices;
    }

    private void evaluate(AlarmRule rule, SensorEntry entry, long tick, double hysteresis, long cooldownTicks, List<Notice> notices) {
        double value = DerivedMetric.compute(rule.observed(), rule.period(), entry.series(), entry.value(), entry.capacity());
        AlarmState state = states.get(rule.id());
        AlarmState.Transition transition = state.update(rule, value, hysteresis);
        if (state.active() && !state.noticed() && state.takeNotice(tick, cooldownTicks)) {
            notices.add(new Notice(rule, true, entry.name(), entry.unit(), value));
        }
        if (transition == AlarmState.Transition.RAISED) {
            changed = true;
        } else if (transition == AlarmState.Transition.CLEARED) {
            changed = true;
            remember(new Line(rule.message(), rule.severity().ordinal(), false));
            if (state.takeClearNotice()) {
                notices.add(new Notice(rule, false, entry.name(), entry.unit(), value));
            }
        }
        int level = state.sounding() ? LEVEL_ALARM : state.active() || rule.near(value, hysteresis) ? LEVEL_NEAR : LEVEL_NORMAL;
        levels.merge(rule.sensorId(), level, Math::max);
    }

    private void remember(Line line) {
        resolved.addFirst(line);
        while (resolved.size() > RESOLVED_LINES) {
            resolved.removeLast();
        }
    }

    public static CompoundTag tagOf(List<AlarmRule> rules, @Nullable Severity redstone) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (AlarmRule rule : rules) {
            CompoundTag ruleTag = new CompoundTag();
            ruleTag.putInt(ID, rule.id());
            ruleTag.putInt(SENSOR, rule.sensorId());
            ruleTag.putString(DERIVED, rule.derived().name());
            ruleTag.putString(COMPARISON, rule.comparison().name());
            ruleTag.putDouble(THRESHOLD, rule.threshold());
            ruleTag.putString(SEVERITY, rule.severity().name());
            ruleTag.putString(MESSAGE, rule.message());
            ruleTag.putInt(HOLD, rule.holdSeconds());
            ruleTag.putString(PERIOD, rule.period().name());
            list.add(ruleTag);
        }
        tag.put(RULES, list);
        tag.putString(REDSTONE, redstone == null ? REDSTONE_OFF : redstone.name());
        return tag;
    }

    public CompoundTag toTag() {
        CompoundTag tag = tagOf(rules, redstone);
        ListTag list = tag.getList(RULES, Tag.TAG_COMPOUND);
        for (int index = 0; index < rules.size(); index++) {
            AlarmState state = states.get(rules.get(index).id());
            list.getCompound(index).putBoolean(ACTIVE, state.active());
            list.getCompound(index).putBoolean(ACKNOWLEDGED, state.acknowledged());
        }
        tag.putInt(NEXT_ID, nextId);
        tag.put(RESOLVED, savedResolved());
        return tag;
    }

    private ListTag savedResolved() {
        ListTag list = new ListTag();
        for (Line line : resolved) {
            CompoundTag lineTag = new CompoundTag();
            lineTag.putString(MESSAGE, line.message());
            lineTag.putInt(SEVERITY, line.severity());
            list.add(lineTag);
        }
        return list;
    }

    private void loadResolved(ListTag list) {
        for (int index = 0; index < Math.min(list.size(), RESOLVED_LINES); index++) {
            CompoundTag lineTag = list.getCompound(index);
            resolved.addLast(new Line(lineTag.getString(MESSAGE), lineTag.getInt(SEVERITY), false));
        }
    }

    public void apply(CompoundTag tag) {
        replace(rulesOf(tag), redstoneOf(tag));
    }

    void load(CompoundTag tag) {
        nextId = Math.max(1, tag.getInt(NEXT_ID));
        ListTag list = tag.getList(RULES, Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(list.size(), MAX_RULES); index++) {
            AlarmRule rule = rule(list.getCompound(index));
            AlarmState state = new AlarmState();
            state.restore(list.getCompound(index).getBoolean(ACTIVE), list.getCompound(index).getBoolean(ACKNOWLEDGED));
            rules.add(rule);
            states.put(rule.id(), state);
            nextId = Math.max(nextId, rule.id() + 1);
        }
        redstone = redstoneOf(tag);
        loadResolved(tag.getList(RESOLVED, Tag.TAG_COMPOUND));
    }

    public static List<AlarmRule> rulesOf(CompoundTag tag) {
        List<AlarmRule> parsed = new ArrayList<>();
        ListTag list = tag.getList(RULES, Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(list.size(), MAX_RULES); index++) {
            parsed.add(rule(list.getCompound(index)));
        }
        return parsed;
    }

    @Nullable
    public static Severity redstoneOf(CompoundTag tag) {
        String name = tag.getString(REDSTONE);
        return name.isEmpty() || name.equals(REDSTONE_OFF) ? null : PanelConfig.constant(Severity.class, name, Severity.CRITICAL);
    }

    private static AlarmRule rule(CompoundTag tag) {
        double threshold = tag.getDouble(THRESHOLD);
        return new AlarmRule(tag.getInt(ID), tag.getInt(SENSOR), PanelConfig.constant(Kind.class, tag.getString(DERIVED), Kind.VALUE),
                PanelConfig.constant(Comparison.class, tag.getString(COMPARISON), Comparison.BELOW), Double.isFinite(threshold) ? threshold : 0,
                PanelConfig.constant(Severity.class, tag.getString(SEVERITY), Severity.WARNING), tag.getString(MESSAGE),
                tag.getInt(HOLD), PanelConfig.constant(Period.class, tag.getString(PERIOD), Period.FIVE_MINUTES));
    }
}
