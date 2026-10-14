package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScreenMultiblock {
    private static final int MAX_COMPONENT = PanelLayout.MAX_WIDTH * PanelLayout.MAX_HEIGHT * 2;

    private record Bounds(int minU, int maxU, int minY, int maxY) {
        int width() {
            return maxU - minU + 1;
        }

        int height() {
            return maxY - minY + 1;
        }

        boolean fits(int blocks) {
            return blocks == width() * height() && width() <= PanelLayout.MAX_WIDTH && height() <= PanelLayout.MAX_HEIGHT;
        }
    }

    private record Donor(@Nullable ChannelKey channel, PanelConfig config) {
    }

    private static final Donor NO_DONOR = new Donor(null, PanelConfig.DEFAULT);

    private ScreenMultiblock() {
    }

    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Nullable
    public static ScreenBlockEntity controller(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ScreenBlock)) {
            return null;
        }
        Direction left = right(state.getValue(ScreenBlock.FACING)).getOpposite();
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (!level.getBlockState(cursor).getValue(ScreenBlock.CONTROLLER)) {
            if (connected(level, cursor.relative(left), state)) {
                cursor.move(left);
            } else if (connected(level, cursor.below(), state)) {
                cursor.move(Direction.DOWN);
            } else {
                return null;
            }
        }
        return level.getBlockEntity(cursor) instanceof ScreenBlockEntity screen ? screen : null;
    }

    static void reform(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ScreenBlock) {
            apply(level, component(level, pos, state), state.getValue(ScreenBlock.FACING), pos, NO_DONOR);
        }
    }

    static void reformAround(ServerLevel level, BlockPos removed, Direction facing, @Nullable ChannelKey channel, PanelConfig config) {
        Set<BlockPos> done = new HashSet<>();
        List<Set<BlockPos>> components = new ArrayList<>();
        Direction right = right(facing);
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
            BlockPos neighbour = removed.relative(side);
            BlockState state = level.getBlockState(neighbour);
            if (!done.contains(neighbour) && state.getBlock() instanceof ScreenBlock && state.getValue(ScreenBlock.FACING) == facing) {
                Set<BlockPos> component = component(level, neighbour, state);
                components.add(component);
                done.addAll(component);
            }
        }
        Donor fallback = channel != null ? new Donor(channel, config) : donor(level, done, null, NO_DONOR);
        components.forEach(component -> apply(level, component, facing, null, fallback));
    }

    private static boolean connected(Level level, BlockPos pos, BlockState like) {
        BlockState state = level.getBlockState(pos);
        return state.is(like.getBlock()) && state.getValue(ScreenBlock.FACING) == like.getValue(ScreenBlock.FACING);
    }

    private static Set<BlockPos> component(Level level, BlockPos start, BlockState like) {
        Direction right = right(like.getValue(ScreenBlock.FACING));
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        pending.add(start.immutable());
        while (!pending.isEmpty() && found.size() <= MAX_COMPONENT) {
            BlockPos current = pending.poll();
            if (found.add(current)) {
                for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
                    BlockPos next = current.relative(side);
                    if (!found.contains(next) && connected(level, next, like)) {
                        pending.add(next);
                    }
                }
            }
        }
        return found;
    }

    private static void apply(ServerLevel level, Set<BlockPos> component, Direction facing, @Nullable BlockPos newcomer, Donor fallback) {
        Direction right = right(facing);
        Bounds bounds = bounds(component, right);
        boolean rectangle = bounds.fits(component.size());
        Donor donor = donor(level, component, newcomer, fallback);
        for (BlockPos pos : component) {
            setShape(level, pos, rectangle ? shapeInside(level.getBlockState(pos), pos, bounds, right) : alone(level.getBlockState(pos)));
        }
        for (BlockPos pos : component) {
            if (level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
                screen.resize(rectangle ? bounds.width() : 1, rectangle ? bounds.height() : 1);
                screen.adopt(donor.channel(), donor.config(), rectangle);
            }
        }
    }

    private static Bounds bounds(Set<BlockPos> component, Direction right) {
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos pos : component) {
            int u = along(pos, right);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        return new Bounds(minU, maxU, minY, maxY);
    }

    private static int along(BlockPos pos, Direction right) {
        return pos.get(right.getAxis()) * right.getAxisDirection().getStep();
    }

    private static boolean isCorner(BlockPos pos, Bounds bounds, Direction right) {
        return along(pos, right) == bounds.minU() && pos.getY() == bounds.minY();
    }

    private static Donor donor(ServerLevel level, Set<BlockPos> component, @Nullable BlockPos newcomer, Donor fallback) {
        ScreenBlockEntity largest = null;
        for (BlockPos pos : component) {
            if (level.getBlockEntity(pos) instanceof ScreenBlockEntity screen && screen.channel() != null && !pos.equals(newcomer)
                    && (largest == null || area(screen) > area(largest))) {
                largest = screen;
            }
        }
        if (largest == null && newcomer != null && level.getBlockEntity(newcomer) instanceof ScreenBlockEntity placed) {
            largest = placed;
        }
        return largest == null ? fallback : new Donor(largest.channel(), largest.config());
    }

    private static int area(ScreenBlockEntity screen) {
        return screen.width() * screen.height();
    }

    private static BlockState shapeInside(BlockState state, BlockPos pos, Bounds bounds, Direction right) {
        int u = along(pos, right);
        return state.setValue(ScreenBlock.CONTROLLER, isCorner(pos, bounds, right))
                .setValue(ScreenBlock.LEFT, u > bounds.minU()).setValue(ScreenBlock.RIGHT, u < bounds.maxU())
                .setValue(ScreenBlock.DOWN, pos.getY() > bounds.minY()).setValue(ScreenBlock.UP, pos.getY() < bounds.maxY());
    }

    private static BlockState alone(BlockState state) {
        return state.setValue(ScreenBlock.CONTROLLER, true).setValue(ScreenBlock.LEFT, false).setValue(ScreenBlock.RIGHT, false)
                .setValue(ScreenBlock.DOWN, false).setValue(ScreenBlock.UP, false);
    }

    private static void setShape(ServerLevel level, BlockPos pos, BlockState shaped) {
        if (level.getBlockState(pos) == shaped) {
            return;
        }
        level.setBlock(pos, shaped, Block.UPDATE_CLIENTS);
        if (!shaped.getValue(ScreenBlock.CONTROLLER)) {
            level.removeBlockEntity(pos);
        }
    }
}
