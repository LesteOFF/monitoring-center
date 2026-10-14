package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.alarm.AlarmRule;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.series.DerivedMetric;
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
public final class AlarmGameTests {
    private static final BlockPos CENTRAL = Scenario.CENTER.west();
    private static final BlockPos BATTERY = Scenario.CENTER.east();
    private static final double LOW_ENERGY = 1000;
    private static final int MAX_SIGNAL = 15;

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void drainingTheBatteryRaisesTheAlarmAndLightsTheCentral(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        CentralBlockEntity battery = scenario.centralOn(BATTERY, new ChannelKey(UUID.randomUUID(), "battery"));
        battery.energy().receiveEnergy(Integer.MAX_VALUE, false);
        scenario.sensorFacing(Scenario.CENTER, Direction.EAST);
        Channel channel = scenario.network().find(scenario.channel());
        int sensorId = scenario.entry(Scenario.CENTER).id();
        channel.alarms().replace(List.of(new AlarmRule(0, sensorId, DerivedMetric.Kind.VALUE, AlarmRule.Comparison.BELOW, LOW_ENERGY,
                AlarmRule.Severity.CRITICAL, "low battery")), AlarmRule.Severity.WARNING);
        helper.startSequence()
                .thenExecute(() -> helper.assertFalse(scenario.emitting(CENTRAL), "no alarm while the battery is full"))
                .thenWaitUntil(() -> helper.assertTrue(scenario.emitting(CENTRAL), "central should emit redstone during the alarm"))
                .thenExecute(() -> helper.assertTrue(channel.alarms().signalStrength() == MAX_SIGNAL, "a critical alarm gives a full comparator signal"))
                .thenExecute(() -> helper.assertTrue(channel.alarms().acknowledge() == 1, "the active alarm should be silenced"))
                .thenWaitUntil(() -> helper.assertFalse(scenario.emitting(CENTRAL), "a silenced alarm stops the redstone"))
                .thenExecute(() -> helper.assertTrue(scenario.inbox().stream().anyMatch(note -> note.contains("low battery")),
                        "offline owner should get the alarm note"))
                .thenSucceed();
    }
}
