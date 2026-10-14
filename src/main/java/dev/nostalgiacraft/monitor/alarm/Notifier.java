package dev.nostalgiacraft.monitor.alarm;

import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.Owners;
import dev.nostalgiacraft.monitor.panel.ValueFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.UUID;

public final class Notifier {
    private static final ChatFormatting[] SEVERITY_COLORS = {ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.RED};

    private Notifier() {
    }

    public static void centralOff(MinecraftServer server, Channel channel) {
        send(server, channel, Component.translatable("alarm.monitoringcenter.central_off", channel.key().name()).withStyle(ChatFormatting.RED),
                SoundEvents.NOTE_BLOCK_BELL);
    }

    public static void alarms(MinecraftServer server, Channel channel, List<AlarmBoard.Notice> notices) {
        MutableComponent message = Component.empty();
        boolean anyRaised = false;
        for (int index = 0; index < notices.size(); index++) {
            if (index > 0) {
                message.append("\n");
            }
            message.append(line(channel, notices.get(index)));
            anyRaised |= notices.get(index).raised();
        }
        send(server, channel, message, anyRaised ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_CHIME);
    }

    public static void deliver(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        MonitorNetwork network = MonitorNetwork.get(server.overworld());
        List<String> notes = network.inbox().take(player.getUUID());
        if (notes.isEmpty()) {
            return;
        }
        network.setDirty();
        player.sendSystemMessage(Component.translatable("alarm.monitoringcenter.inbox", notes.size()).withStyle(ChatFormatting.YELLOW));
        for (String note : notes) {
            Component parsed = Component.Serializer.fromJson(note, server.registryAccess());
            if (parsed != null) {
                player.sendSystemMessage(parsed);
            }
        }
    }

    private static Component line(Channel channel, AlarmBoard.Notice notice) {
        AlarmRule rule = notice.rule();
        if (!notice.raised()) {
            return Component.translatable("alarm.monitoringcenter.cleared", channel.key().name(), rule.message()).withStyle(ChatFormatting.GREEN);
        }
        String reading = (ValueFormat.derived(rule.derived(), notice.value()) + " " + ValueFormat.unit(rule.derived(), notice.unit())).strip();
        return Component.translatable("alarm.monitoringcenter.raised", channel.key().name(), rule.message(), notice.sensorName(), reading)
                .withStyle(SEVERITY_COLORS[rule.severity().ordinal()]);
    }

    private static void send(MinecraftServer server, Channel channel, Component message, Holder<SoundEvent> sound) {
        MonitorNetwork inboxHolder = MonitorNetwork.get(server.overworld());
        for (UUID member : Owners.members(server, channel.key().owner())) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(message);
                online.playNotifySound(sound.value(), SoundSource.MASTER, 1f, 1f);
            } else {
                inboxHolder.inbox().add(member, Component.Serializer.toJson(message, server.registryAccess()), System.currentTimeMillis());
                inboxHolder.setDirty();
            }
        }
    }
}
