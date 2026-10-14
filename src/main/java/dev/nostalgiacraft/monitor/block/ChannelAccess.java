package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.Owners;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ChannelAccess {
    private static final double REACH_SQUARED = 64;

    private ChannelAccess() {
    }

    public static boolean allowed(ServerPlayer player, @Nullable ChannelKey channel) {
        if (owns(player, channel)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("message.monitoringcenter.not_owner"), true);
        return false;
    }

    public static boolean owns(ServerPlayer player, @Nullable ChannelKey channel) {
        return channel == null || Owners.belongs(player, channel.owner());
    }

    @Nullable
    public static ChannelMember member(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ScreenBlock) {
            return ScreenMultiblock.controller(level, pos);
        }
        return level.getBlockEntity(pos) instanceof ChannelMember member ? member : null;
    }

    public static boolean reachable(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos) && player.distanceToSqr(pos.getCenter()) <= REACH_SQUARED;
    }
}
