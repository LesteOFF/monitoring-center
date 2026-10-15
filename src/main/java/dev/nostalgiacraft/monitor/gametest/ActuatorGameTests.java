package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.alarm.AlarmRule;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Comparison;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Severity;
import dev.nostalgiacraft.monitor.block.ActuatorBlockEntity;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.data.ActuatorSettings;
import dev.nostalgiacraft.monitor.data.ActuatorSettings.Source;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(MonitoringCenter.MODID)
@PrefixGameTestTemplate(false)
public final class ActuatorGameTests {
    private static final BlockPos CENTRAL = Scenario.CENTER.west();
    private static final BlockPos BATTERY = Scenario.CENTER.east();
    private static final BlockPos ACTUATOR = Scenario.CENTER.north();
    private static final double LOW_ENERGY = 1000;
    private static final int AFTER_TWO_EVALUATIONS = 2 * 20;

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void anActuatorFollowingARuleStaysOnEvenWhenTheAlarmIsSilenced(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        Channel channel = drainingBattery(helper, scenario);
        int ruleId = lowBatteryRule(scenario, channel);
        scenario.actuator(ACTUATOR).configure(followRule(ruleId, false));
        helper.startSequence()
                .thenExecute(() -> helper.assertFalse(scenario.emitting(ACTUATOR), "no signal while the battery is full"))
                .thenWaitUntil(() -> helper.assertTrue(scenario.emitting(ACTUATOR), "the actuator should turn on with the rule"))
                .thenExecute(() -> channel.alarms().acknowledge())
                .thenIdle(AFTER_TWO_EVALUATIONS)
                .thenExecute(() -> helper.assertTrue(scenario.emitting(ACTUATOR), "silencing the alarm must not stop the actuator"))
                .thenSucceed();
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void anInvertedActuatorTurnsOffWhenTheRuleFires(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        Channel channel = drainingBattery(helper, scenario);
        int ruleId = lowBatteryRule(scenario, channel);
        scenario.actuator(ACTUATOR).configure(followRule(ruleId, true));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scenario.emitting(ACTUATOR), "inverted output is on while the rule is quiet"))
                .thenWaitUntil(() -> helper.assertFalse(scenario.emitting(ACTUATOR), "inverted output turns off when the rule fires"))
                .thenSucceed();
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void anActuatorWithItsOwnLimitFollowsASensorWithoutAnyRule(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        Channel channel = drainingBattery(helper, scenario);
        int sensorId = scenario.entry(Scenario.CENTER).id();
        ActuatorBlockEntity actuator = scenario.actuator(ACTUATOR);
        actuator.configure(new ActuatorSettings(Source.SENSOR, 0, lowBattery(sensorId), false));
        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(channel.alarms().rules().isEmpty(), "the channel has no alarm rules"))
                .thenExecute(() -> helper.assertFalse(scenario.emitting(ACTUATOR), "no signal while the battery is full"))
                .thenWaitUntil(() -> helper.assertTrue(scenario.emitting(ACTUATOR), "the actuator should follow its own limit"))
                .thenSucceed();
    }

    private static Channel drainingBattery(GameTestHelper helper, Scenario scenario) {
        scenario.keepCharged(scenario.central(CENTRAL));
        CentralBlockEntity battery = scenario.centralOn(BATTERY, new ChannelKey(UUID.randomUUID(), "battery"));
        battery.energy().receiveEnergy(Integer.MAX_VALUE, false);
        scenario.sensorFacing(Scenario.CENTER, Direction.EAST);
        return scenario.network().find(scenario.channel());
    }

    private static int lowBatteryRule(Scenario scenario, Channel channel) {
        AlarmRule rule = lowBattery(scenario.entry(Scenario.CENTER).id());
        channel.alarms().replace(List.of(rule), null);
        return channel.alarms().rules().getFirst().id();
    }

    private static AlarmRule lowBattery(int sensorId) {
        return new AlarmRule(0, sensorId, Kind.VALUE, Comparison.BELOW, LOW_ENERGY, Severity.WARNING, "low battery");
    }

    private static ActuatorSettings followRule(int ruleId, boolean inverted) {
        return new ActuatorSettings(Source.RULE, ruleId, ActuatorSettings.DEFAULT.condition(), inverted);
    }
}
