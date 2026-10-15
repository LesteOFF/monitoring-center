package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.Owners;
import dev.nostalgiacraft.monitor.data.PanelSync;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.registry.ChannelKeyItem;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ActuatorBlock extends Block implements EntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int LIGHT_WHEN_POWERED = 7;
    private static final int MAX_SIGNAL = 15;

    public ActuatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ActuatorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlocks.ACTUATOR_ENTITY.get()) {
            return null;
        }
        return (tickingLevel, pos, tickingState, actuator) -> ((ActuatorBlockEntity) actuator).serverTick((ServerLevel) tickingLevel);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof ActuatorBlockEntity actuator) {
            actuator.joinChannel(new ChannelKey(Owners.ownerOf(player), ChannelKey.DEFAULT_NAME));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ActuatorBlockEntity actuator
                && ChannelAccess.allowed(server, actuator.channel())) {
            ChannelKey key = actuator.channel() != null ? actuator.channel() : new ChannelKey(Owners.ownerOf(server), ChannelKey.DEFAULT_NAME);
            Channel channel = MonitorNetwork.get(server.serverLevel()).find(key);
            if (channel != null) {
                PanelSync.sendSnapshot(server, key);
            }
            CompoundTag rules = AlarmBoard.tagOf(channel == null ? List.of() : channel.alarms().rules(), null);
            PacketDistributor.sendToPlayer(server, new Payloads.OpenActuator(pos, key, actuator.settings().toTag(), rules));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof ChannelKeyItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? MAX_SIGNAL : 0;
    }
}
