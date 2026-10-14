package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.Owners;
import dev.nostalgiacraft.monitor.data.PanelSync;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.registry.ChannelKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScreenBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final double THICKNESS = 3 / 16.0;
    private static final int OPAQUE = 0xFF000000;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, box(0, 0, 13, 16, 16, 16),
            Direction.SOUTH, box(0, 0, 0, 16, 16, 3),
            Direction.WEST, box(13, 0, 0, 16, 16, 16),
            Direction.EAST, box(0, 0, 0, 3, 16, 16)));

    public ScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONTROLLER, true)
                .setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTROLLER, LEFT, RIGHT, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CONTROLLER) ? new ScreenBlockEntity(pos, state) : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ScreenMultiblock.reform(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        ScreenBlockEntity removed = level.getBlockEntity(pos) instanceof ScreenBlockEntity screen ? screen : null;
        ChannelKey channel = removed == null ? null : removed.channel();
        PanelConfig config = removed == null ? PanelConfig.DEFAULT : removed.config();
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this) && level instanceof ServerLevel server) {
            ScreenMultiblock.reformAround(server, pos, state.getValue(FACING), channel, config);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        ScreenBlockEntity controller = ScreenMultiblock.controller(level, pos);
        if (!(placer instanceof ServerPlayer player) || controller == null) {
            return;
        }
        UUID owner = Owners.ownerOf(player);
        ChannelKey kept = controller.channel();
        controller.joinChannel(kept != null && kept.owner().equals(owner) ? kept : new ChannelKey(owner, ChannelKey.DEFAULT_NAME));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ScreenBlockEntity screen && screen.customized()) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    BlockItem.setBlockEntityData(drop, screen.getType(), screen.keepsake());
                }
            }
        }
        return drops;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ChannelKeyItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ScreenBlockEntity controller = ScreenMultiblock.controller(level, pos);
        if (player instanceof ServerPlayer server && controller != null && ChannelAccess.allowed(server, controller.channel())) {
            controller.paintRow(hit.getLocation().y, dye.getDyeColor().getTextColor() | OPAQUE);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ScreenBlockEntity controller = ScreenMultiblock.controller(level, pos);
        if (player instanceof ServerPlayer server && controller != null && ChannelAccess.allowed(server, controller.channel())
                && !controller.touch(hit.getLocation())) {
            PanelSync.sendSnapshot(server, controller.channel());
            PacketDistributor.sendToPlayer(server, new Payloads.OpenPanel(controller.getBlockPos()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
