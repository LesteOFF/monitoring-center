package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MonitoringCenter.MODID)
@PrefixGameTestTemplate(false)
public final class PowerGameTests {
    private static final BlockPos SENSOR = Scenario.CENTER.east();
    private static final BlockPos REDSTONE_SENSOR = Scenario.CENTER.above();
    private static final int REDSTONE_BLOCK_SIGNAL = 15;
    private static final int ROTATION_TICKS = 40;

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void sensorNextToTheCentralReadsItsBattery(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        CentralBlockEntity central = scenario.central(Scenario.CENTER);
        scenario.keepCharged(central);
        scenario.sensorFacing(SENSOR, Direction.WEST);
        helper.succeedWhen(() -> {
            SensorEntry entry = scenario.entry(SENSOR);
            if (!(entry.value() > 0) || entry.capacity() != central.energy().getMaxEnergyStored()) {
                throw new GameTestAssertException("battery not read: " + entry.value() + " of " + entry.capacity());
            }
        });
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void centralWithoutEnergyStopsTheSampling(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        CentralBlockEntity central = scenario.central(Scenario.CENTER);
        central.energy().receiveEnergy(Integer.MAX_VALUE, false);
        helper.setBlock(REDSTONE_SENSOR.above(), Blocks.REDSTONE_BLOCK);
        scenario.sensorFacing(REDSTONE_SENSOR, Direction.UP).configure("", "redstone", 0, 1);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scenario.powered(), "central should start powered"))
                .thenWaitUntil(() -> helper.assertFalse(scenario.powered(), "battery should run out"))
                .thenWaitUntil(() -> helper.assertTrue(Double.isNaN(scenario.entry(REDSTONE_SENSOR).value()), "sampling should stop"))
                .thenExecute(() -> helper.assertFalse(scenario.inbox().isEmpty(), "offline owner should get the Central off note"))
                .thenSucceed();
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void rotationKeepsTheCentralOnWithoutEnergy(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        CentralBlockEntity central = scenario.central(Scenario.CENTER);
        helper.onEachTick(() -> central.acceptRotation(helper.getLevel().getGameTime()));
        helper.runAfterDelay(ROTATION_TICKS, () -> {
            helper.assertTrue(scenario.powered(), "rotation should power the channel");
            helper.assertTrue(central.energy().getEnergyStored() == central.energy().getMaxEnergyStored(), "rotation keeps the battery full");
            helper.succeed();
        });
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void aChannelAcceptsOnlyOneCentral(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.central(Scenario.CENTER);
        helper.setBlock(SENSOR, dev.nostalgiacraft.monitor.registry.ModBlocks.CENTRAL.get());
        CentralBlockEntity second = (CentralBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(SENSOR));
        helper.assertFalse(second.joinChannel(scenario.channel()), "second central must be refused");
        helper.succeed();
    }
}
