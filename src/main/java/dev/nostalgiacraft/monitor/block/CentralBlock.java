package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.CentralConfig;
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
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class CentralBlock extends Block implements EntityBlock {
    private static final int MAX_SIGNAL = 15;

    public CentralBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentralBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlocks.CENTRAL_ENTITY.get()) {
            return null;
        }
        return (tickingLevel, pos, tickingState, central) -> ((CentralBlockEntity) central).serverTick((ServerLevel) tickingLevel);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof CentralBlockEntity central
                && !central.joinChannel(new ChannelKey(Owners.ownerOf(player), ChannelKey.DEFAULT_NAME))) {
            player.displayClientMessage(Component.translatable("message.monitoringcenter.central_exists", ChannelKey.DEFAULT_NAME), true);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CentralBlockEntity central
                && ChannelAccess.allowed(server, central.channel())) {
            Channel channel = MonitorNetwork.get(server.serverLevel()).find(central.channel());
            if (server.isShiftKeyDown() && channel != null && silenced(server, channel)) {
                return InteractionResult.SUCCESS;
            }
            if (channel == null) {
                ChannelKey wanted = new ChannelKey(Owners.ownerOf(server), ChannelKey.DEFAULT_NAME);
                PacketDistributor.sendToPlayer(server, new Payloads.OpenCentral(pos, wanted, CentralConfig.unlinked()));
            } else {
                PanelSync.sendSnapshot(server, channel.key());
                PacketDistributor.sendToPlayer(server, new Payloads.OpenCentral(pos, channel.key(), CentralConfig.of(channel)));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean silenced(ServerPlayer player, Channel channel) {
        int alarms = channel.alarms().acknowledge();
        if (alarms == 0) {
            return false;
        }
        MonitorNetwork.get(player.serverLevel()).setDirty();
        player.displayClientMessage(Component.translatable("message.monitoringcenter.alarms_silenced", alarms), true);
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CentralBlockEntity central ? central.analogSignal() : 0;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CentralBlockEntity central && central.emitting() ? MAX_SIGNAL : 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof ChannelKeyItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof CentralBlockEntity central) {
            MonitorNetwork.get(server).detachCentral(central.channel(), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
