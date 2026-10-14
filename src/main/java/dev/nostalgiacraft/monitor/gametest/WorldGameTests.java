package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MonitoringCenter.MODID)
@PrefixGameTestTemplate(false)
public final class WorldGameTests {
    private static final BlockPos CENTRAL = Scenario.CENTER.north();
    private static final int RIPE = CropBlock.MAX_AGE;
    private static final int DROPPED = 5;
    private static final double HALF_RIPE = 50;

    private static void plant(GameTestHelper helper, int x, int z, int age) {
        helper.setBlock(new BlockPos(x, 0, z), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(x, 1, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void cropSensorReportsTheShareOfRipePlants(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        plant(helper, 0, 0, RIPE);
        plant(helper, 2, 0, RIPE);
        plant(helper, 0, 2, 0);
        plant(helper, 2, 2, 0);
        scenario.sensorFacing(Scenario.CENTER, Direction.DOWN).configure("", "crops", 0, 1);
        helper.succeedWhen(() -> scenario.assertReading(Scenario.CENTER, HALF_RIPE));
    }

    @GameTest(template = Scenario.EMPTY, timeoutTicks = Scenario.TIMEOUT)
    public static void groundItemSensorCountsDroppedStacks(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        scenario.keepCharged(scenario.central(CENTRAL));
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        Vec3 drop = helper.absoluteVec(new Vec3(1.5, 1.2, 1.5));
        ItemEntity dropped = new ItemEntity(helper.getLevel(), drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND, DROPPED), 0, 0, 0);
        dropped.setUnlimitedLifetime();
        helper.getLevel().addFreshEntity(dropped);
        scenario.sensorFacing(Scenario.CENTER, Direction.DOWN).configure("", "ground_items", 0, 1);
        helper.succeedWhen(() -> scenario.assertReading(Scenario.CENTER, DROPPED));
    }
}
