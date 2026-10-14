package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {
    private static final BlockPos SENSOR = Scenario.CENTER.east();
    private static final List<String> REQUIRED_MODS = List.of("mekanism", "ae2", "create");

    private IntegrationGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        if (REQUIRED_MODS.stream().allMatch(ModList.get()::isLoaded)) {
            event.register(IntegrationGameTests.class);
        }
    }

    @GameTest(templateNamespace = MonitoringCenter.MODID, template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void mekanismEnergyMatchesWhatTheCubeReportsInForgeEnergy(GameTestHelper helper) {
        SensorBlockEntity sensor = sensorAt(helper, "mekanism:basic_energy_cube", "energy");
        IEnergyStorage reported = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(Scenario.CENTER), null);
        Sample sample = reading(helper, sensor);
        helper.assertTrue(reported != null && sample.capacity() == reported.getMaxEnergyStored(),
                "capacity was " + sample.capacity() + " but the cube reports " + (reported == null ? "nothing" : reported.getMaxEnergyStored()));
        helper.succeed();
    }

    @GameTest(templateNamespace = MonitoringCenter.MODID, template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void mekanismMachineReportsItsProgress(GameTestHelper helper) {
        Sample sample = reading(helper, sensorAt(helper, "mekanism:enrichment_chamber", "mek_progress"));
        helper.assertTrue(sample.value() == 0 && sample.capacity() == 100, "idle machine read " + sample);
        helper.succeed();
    }

    @GameTest(templateNamespace = MonitoringCenter.MODID, template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void mekanismLooseCasingIsNotAFormedMultiblock(GameTestHelper helper) {
        Sample sample = reading(helper, sensorAt(helper, "mekanism:induction_casing", "mek_formed"));
        helper.assertTrue(sample.value() == 0 && sample.capacity() == 1, "loose casing read " + sample);
        helper.succeed();
    }

    @GameTest(templateNamespace = MonitoringCenter.MODID, template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void createVaultIsReadAsAnInventory(GameTestHelper helper) {
        SensorBlockEntity sensor = sensorAt(helper, "create:item_vault", Metrics.ITEMS);
        helper.succeedWhen(() -> helper.assertTrue(reading(helper, sensor).capacity() > 0, "vault should offer room for items"));
    }

    @GameTest(templateNamespace = MonitoringCenter.MODID, template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void ae2GridReportsItsEnergy(GameTestHelper helper) {
        SensorBlockEntity sensor = sensorAt(helper, "ae2:creative_energy_cell", "ae2_energy");
        helper.succeedWhen(() -> helper.assertTrue(reading(helper, sensor).capacity() > 0, "grid should report stored energy"));
    }

    @GameTest(templateNamespace = MonitoringCenter.MODID, template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void ae2GridCountsStoredTypes(GameTestHelper helper) {
        SensorBlockEntity sensor = sensorAt(helper, "ae2:creative_energy_cell", "ae2_types");
        helper.succeedWhen(() -> helper.assertTrue(reading(helper, sensor).value() == 0, "empty grid stores no types"));
    }

    private static SensorBlockEntity sensorAt(GameTestHelper helper, String blockId, String metric) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
        if (block == Blocks.AIR) {
            throw new GameTestAssertException("missing block " + blockId);
        }
        helper.setBlock(Scenario.CENTER, block);
        SensorBlockEntity sensor = new Scenario(helper).sensorFacing(SENSOR, Direction.WEST);
        sensor.configure("", metric, 0, 1);
        return sensor;
    }

    private static Sample reading(GameTestHelper helper, SensorBlockEntity sensor) {
        Sample sample = sensor.read(helper.getLevel());
        if (sample == null) {
            throw new GameTestAssertException("no reading for " + sensor.metricId());
        }
        return sample;
    }
}
