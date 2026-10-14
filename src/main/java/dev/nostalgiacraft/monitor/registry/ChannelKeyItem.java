package dev.nostalgiacraft.monitor.registry;

import dev.nostalgiacraft.monitor.block.ChannelAccess;
import dev.nostalgiacraft.monitor.block.ChannelMember;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ChannelKeyItem extends Item {
    public ChannelKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ChannelMember member = ChannelAccess.member(context.getLevel(), context.getClickedPos());
        if (member == null) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player && ChannelAccess.allowed(player, member.channel())) {
            if (player.isShiftKeyDown()) {
                copy(player, context.getItemInHand(), member.channel());
            } else {
                paste(player, stored(context.getItemInHand()), member);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ChannelKey channel = stored(stack);
        tooltip.add(channel == null ? Component.translatable("item.monitoringcenter.channel_key.empty").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.monitoringcenter.channel_key.channel", channel.name()).withStyle(ChatFormatting.GOLD));
    }

    private static void copy(ServerPlayer player, ItemStack stack, @Nullable ChannelKey channel) {
        if (channel != null) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, channel::save);
            player.displayClientMessage(Component.translatable("message.monitoringcenter.channel_copied", channel.name()), true);
        }
    }

    private static void paste(ServerPlayer player, @Nullable ChannelKey channel, ChannelMember member) {
        if (channel == null || !ChannelAccess.allowed(player, channel)) {
            return;
        }
        String message = member.joinChannel(channel) ? "message.monitoringcenter.channel_applied" : "message.monitoringcenter.channel_full";
        player.displayClientMessage(Component.translatable(message, channel.name()), true);
    }

    @Nullable
    private static ChannelKey stored(ItemStack stack) {
        return ChannelKey.load(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }
}
