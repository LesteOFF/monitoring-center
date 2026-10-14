package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.ScreenBlock;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.ScreenMultiblock;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import dev.nostalgiacraft.monitor.series.DerivedMetric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(MonitoringCenter.MODID)
@PrefixGameTestTemplate(false)
public final class ScreenGameTests {
    private static final Direction FACING = Direction.NORTH;
    private static final int WALL_WIDTH = 3;
    private static final int WALL_HEIGHT = 2;
    private static final int FORMATION_TICKS = 4;

    private static List<BlockPos> wall(GameTestHelper helper) {
        List<BlockPos> wall = new ArrayList<>();
        for (int x = 0; x < WALL_WIDTH; x++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                BlockPos pos = new BlockPos(x, y, 1);
                helper.setBlock(pos, ModBlocks.SCREEN.get().defaultBlockState().setValue(ScreenBlock.FACING, FACING));
                wall.add(pos);
            }
        }
        return wall;
    }

    private static List<ScreenBlockEntity> controllers(GameTestHelper helper, List<BlockPos> wall) {
        List<ScreenBlockEntity> controllers = new ArrayList<>();
        for (BlockPos pos : wall) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ScreenBlockEntity screen) {
                controllers.add(screen);
            }
        }
        return controllers;
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aFullRectangleBecomesOnePanelControlledFromTheBottomLeft(GameTestHelper helper) {
        List<BlockPos> wall = wall(helper);
        helper.runAfterDelay(FORMATION_TICKS, () -> {
            List<ScreenBlockEntity> controllers = controllers(helper, wall);
            if (controllers.size() != 1) {
                throw new GameTestAssertException("expected one controller, found " + controllers.size());
            }
            ScreenBlockEntity controller = controllers.getFirst();
            Direction left = ScreenMultiblock.right(FACING).getOpposite();
            helper.assertTrue(controller.width() == WALL_WIDTH && controller.height() == WALL_HEIGHT,
                    "panel is " + controller.width() + "x" + controller.height());
            helper.assertFalse(helper.getLevel().getBlockState(controller.getBlockPos().relative(left)).getBlock() instanceof ScreenBlock,
                    "controller must be the leftmost block");
            helper.assertFalse(helper.getLevel().getBlockState(controller.getBlockPos().below()).getBlock() instanceof ScreenBlock,
                    "controller must be the lowest block");
            helper.succeed();
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void theFrameOnlyStaysOnTheOuterEdgeOfThePanel(GameTestHelper helper) {
        List<BlockPos> wall = wall(helper);
        helper.runAfterDelay(FORMATION_TICKS, () -> {
            for (BlockPos pos : wall) {
                BlockState state = helper.getBlockState(pos);
                int joined = 0;
                for (BooleanProperty side : List.of(ScreenBlock.LEFT, ScreenBlock.RIGHT, ScreenBlock.UP, ScreenBlock.DOWN)) {
                    joined += state.getValue(side) ? 1 : 0;
                }
                boolean middleColumn = pos.getX() == 1;
                helper.assertTrue(joined == (middleColumn ? 3 : 2), "block " + pos + " joins " + joined + " neighbours");
                helper.assertTrue(state.getValue(ScreenBlock.UP) == (pos.getY() == 1), "block " + pos + " has the wrong vertical join");
            }
            helper.setBlock(wall.removeLast(), Blocks.AIR);
            for (BlockPos pos : wall) {
                BlockState state = helper.getBlockState(pos);
                helper.assertFalse(state.getValue(ScreenBlock.LEFT) || state.getValue(ScreenBlock.RIGHT) || state.getValue(ScreenBlock.UP)
                        || state.getValue(ScreenBlock.DOWN), "block " + pos + " should stand alone");
            }
            helper.succeed();
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aBrokenScreenCarriesItsConfigurationInTheItem(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        BlockPos pos = new BlockPos(0, 1, 1);
        helper.setBlock(pos, ModBlocks.SCREEN.get().defaultBlockState().setValue(ScreenBlock.FACING, FACING));
        ScreenBlockEntity screen = (ScreenBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        screen.joinChannel(scenario.channel());
        PanelConfig custom = new PanelConfig(PanelConfig.Mode.CARDS, PanelConfig.Theme.CYAN, DerivedMetric.Period.SIX_HOURS, 1,
                List.of(PanelConfig.Row.of(7).withIcon("item:minecraft:iron_ingot")));
        screen.setConfig(custom);
        List<ItemStack> drops = Block.getDrops(screen.getBlockState(), helper.getLevel(), screen.getBlockPos(), screen);
        CustomData kept = drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(kept != null, "the dropped Screen lost its configuration");
        ScreenBlockEntity restored = new ScreenBlockEntity(screen.getBlockPos(), screen.getBlockState());
        kept.loadInto(restored, helper.getLevel().registryAccess());
        helper.assertTrue(custom.equals(restored.config()), "restored " + restored.config());
        helper.assertTrue(scenario.channel().equals(restored.channel()), "restored channel " + restored.channel());
        helper.succeed();
    }

    @GameTest(template = Scenario.EMPTY)
    public static void anIncompleteRectangleFallsBackToSingleBlockPanels(GameTestHelper helper) {
        List<BlockPos> wall = wall(helper);
        helper.runAfterDelay(FORMATION_TICKS, () -> {
            helper.setBlock(wall.removeLast(), Blocks.AIR);
            List<ScreenBlockEntity> controllers = controllers(helper, wall);
            helper.assertTrue(controllers.size() == wall.size(), "every block should control itself, found " + controllers.size());
            helper.assertTrue(controllers.stream().allMatch(screen -> screen.width() == 1 && screen.height() == 1), "panels must be 1x1");
            helper.succeed();
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void thePanelKeepsTheChannelWhenItGrows(GameTestHelper helper) {
        Scenario scenario = new Scenario(helper);
        BlockPos first = new BlockPos(0, 1, 1);
        helper.setBlock(first, ModBlocks.SCREEN.get().defaultBlockState().setValue(ScreenBlock.FACING, FACING));
        ((ScreenBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(first))).joinChannel(scenario.channel());
        List<BlockPos> wall = wall(helper);
        helper.runAfterDelay(FORMATION_TICKS, () -> {
            List<ScreenBlockEntity> controllers = controllers(helper, wall);
            helper.assertTrue(controllers.size() == 1 && scenario.channel().equals(controllers.getFirst().channel()), "channel lost on merge");
            helper.assertTrue(scenario.network().find(scenario.channel()).panels().size() == 1, "channel should track exactly one panel");
            helper.succeed();
        });
    }
}
