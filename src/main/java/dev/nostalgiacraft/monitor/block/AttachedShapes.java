package dev.nostalgiacraft.monitor.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public final class AttachedShapes {
    private static final int FULL = 16;

    public record Layer(int inset, int from, int to) {
    }

    private AttachedShapes() {
    }

    public static Map<Direction, VoxelShape> facing(Layer... layers) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (Layer layer : layers) {
                shape = Shapes.or(shape, box(facing, layer));
            }
            shapes.put(facing, shape);
        }
        return shapes;
    }

    private static VoxelShape box(Direction facing, Layer layer) {
        boolean towardsOrigin = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        int near = towardsOrigin ? layer.from() : FULL - layer.to();
        int far = towardsOrigin ? layer.to() : FULL - layer.from();
        int low = layer.inset();
        int high = FULL - layer.inset();
        return switch (facing.getAxis()) {
            case X -> Block.box(near, low, low, far, high, high);
            case Y -> Block.box(low, near, low, high, far, high);
            case Z -> Block.box(low, low, near, high, high, far);
        };
    }
}
