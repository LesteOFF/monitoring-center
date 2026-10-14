package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.SumSpec;
import dev.nostalgiacraft.monitor.metric.Metrics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MonitoringCenter.MODID)
@PrefixGameTestTemplate(false)
public final class SensorGameTests {
    private static final BlockPos SENSOR = Scenario.CENTER.east();
    private static final BlockPos CENTRAL = Scenario.CENTER.north();
    private static final int IRON = 5;
    private static final int PLANKS = 7;
    private static final int BUCKET = 1000;
    private static final int REDSTONE_BLOCK_SIGNAL = 15;
    private static final int REFILL_AT = 60;
    private static final double SLOWEST_INFLOW = 15;
    private static final double FASTEST_INFLOW = 21;

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void sensorCountsTheFilteredItemOfTheNeighbourInventory(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.CHEST);
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(Scenario.CENTER));
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, IRON));
        chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 3));
        SensorBlockEntity sensor = scenario.sensorFacing(SENSOR, Direction.WEST);
        sensor.setFilter(new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> scenario.assertReading(SENSOR, IRON));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void aTagFilterCountsEveryItemOfTheTag(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.CHEST);
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(Scenario.CENTER));
        chest.setItem(0, new ItemStack(Items.OAK_PLANKS, PLANKS));
        chest.setItem(1, new ItemStack(Items.BIRCH_PLANKS, PLANKS));
        chest.setItem(2, new ItemStack(Items.GOLD_INGOT, 3));
        SensorBlockEntity sensor = scenario.sensorFacing(SENSOR, Direction.WEST);
        sensor.setFilter(new ItemStack(Items.OAK_PLANKS));
        sensor.setFilterTag(ItemTags.PLANKS.location().toString());
        helper.succeedWhen(() -> scenario.assertReading(SENSOR, 2 * PLANKS));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void aTagTheFilterItemLacksIsIgnored(GameTestHelper helper) {
        SensorBlockEntity sensor = new Scenario(helper).sensorFacing(SENSOR, Direction.WEST);
        sensor.setFilter(new ItemStack(Items.GOLD_INGOT));
        sensor.setFilterTag(ItemTags.PLANKS.location().toString());
        helper.assertTrue(sensor.filterTag() == null, "gold is not a plank");
        helper.succeed();
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void fluidTotalReadsAFullCauldron(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        scenario.sensorFacing(SENSOR, Direction.WEST).configure("", "fluid_total", 0, 1);
        helper.succeedWhen(() -> scenario.assertReading(SENSOR, BUCKET));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void itemFlowCountsWhatEntersEvenWhenTheStockIsEmptied(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.CHEST);
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(Scenario.CENTER));
        scenario.sensorFacing(SENSOR, Direction.WEST).configure("", Metrics.ITEM_FLOW, 0, 1);
        helper.onEachTick(() -> chest.setItem(0, new ItemStack(Items.IRON_INGOT, chest.getItem(0).getCount() % REFILL_AT + 1)));
        helper.succeedWhen(() -> assertAboutTwentyPerSecond(scenario.entry(SENSOR).value()));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void itemOutCountsWhatLeavesAndIgnoresWhatEnters(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.CHEST);
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(Scenario.CENTER));
        scenario.sensorFacing(SENSOR, Direction.WEST).configure("", Metrics.ITEM_OUT, 0, 1);
        helper.onEachTick(() -> {
            int left = chest.getItem(0).getCount() - 1;
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, left > 0 ? left : REFILL_AT));
        });
        helper.succeedWhen(() -> assertAboutTwentyPerSecond(scenario.entry(SENSOR).value()));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void automaticPrefersTheItemCountOfAStorage(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        helper.setBlock(Scenario.CENTER, Blocks.CHEST);
        SensorBlockEntity sensor = scenario.sensorFacing(SENSOR, Direction.WEST);
        helper.assertTrue(Metrics.ITEMS.equals(sensor.metric(helper.getLevel()).id()), "automatic chose " + sensor.metricId());
        helper.succeed();
    }

    private static void assertAboutTwentyPerSecond(double rate) {
        if (!(rate >= SLOWEST_INFLOW && rate <= FASTEST_INFLOW)) {
            throw new GameTestAssertException("expected about 20 items per second but was " + rate);
        }
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void sensorFallsBackToRedstoneWhenNothingElseIsReadable(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.REDSTONE_BLOCK);
        scenario.sensorFacing(SENSOR, Direction.WEST);
        helper.succeedWhen(() -> scenario.assertReading(SENSOR, REDSTONE_BLOCK_SIGNAL));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void aSumAddsEverySensorOfTheSameMetric(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(Scenario.CENTER, Blocks.REDSTONE_BLOCK);
        scenario.sensorFacing(SENSOR, Direction.WEST);
        scenario.sensorFacing(Scenario.CENTER.west(), Direction.EAST);
        Channel channel = scenario.network().find(scenario.channel());
        channel.replaceSums(List.of(new SumSpec("Total", Metrics.REDSTONE)));
        helper.succeedWhen(() -> scenario.assertValue(channel.sums().getFirst().value(), 2 * REDSTONE_BLOCK_SIGNAL));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void removingTheSensorDropsItsEntry(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.sensorFacing(SENSOR, Direction.WEST);
        helper.setBlock(SENSOR, Blocks.AIR);
        if (scenario.network().find(scenario.channel()) != null) {
            throw new GameTestAssertException("channel should be discarded with its last sensor");
        }
        helper.succeed();
    }
}
