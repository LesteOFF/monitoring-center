package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.series.Series;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PanelSync {
    public static final int VIEW_DISTANCE = 48;
    private static final int PANEL_MARGIN = 8;
    private static final double VIEW_DISTANCE_SQUARED = (VIEW_DISTANCE + PANEL_MARGIN) * (VIEW_DISTANCE + PANEL_MARGIN);

    private PanelSync() {
    }

    public static void sendSnapshot(ServerPlayer player, @Nullable ChannelKey key) {
        Channel channel = MonitorNetwork.get(player.serverLevel()).find(key);
        if (channel != null) {
            PacketDistributor.sendToPlayer(player, snapshot(channel, player.serverLevel().getGameTime()));
        }
    }

    public static void sendHistory(ServerPlayer player, ChannelKey key, int sensorId) {
        Channel channel = MonitorNetwork.get(player.serverLevel()).find(key);
        SensorEntry entry = channel == null || !channel.observers().contains(player.getUUID()) ? null : channel.sensor(sensorId);
        if (entry == null) {
            return;
        }
        List<float[]> tiers = new ArrayList<>();
        for (Series.Stat stat : Series.Stat.values()) {
            tiers.add(entry.series().minutes(stat).toArray());
        }
        for (Series.Stat stat : Series.Stat.values()) {
            tiers.add(entry.series().hours(stat).toArray());
        }
        PacketDistributor.sendToPlayer(player, new Payloads.History(key, sensorId, tiers));
    }

    static void run(ServerLevel level, Collection<Channel> channels) {
        long tick = level.getGameTime();
        for (Channel channel : channels) {
            if (!channel.panels().isEmpty() || !channel.observers().isEmpty()) {
                sync(level, channel, tick);
            }
        }
    }

    private static void sync(ServerLevel level, Channel channel, long tick) {
        List<ServerPlayer> watching = watchers(level, channel);
        Set<UUID> previous = channel.observers();
        Set<UUID> current = new HashSet<>();
        if (!watching.isEmpty()) {
            boolean stale = channel.takeStaleLayout();
            Payloads.ChannelDelta delta = delta(channel, tick);
            boolean powerChanged = channel.takePowerChange(delta.powered());
            boolean changed = powerChanged || delta.alarmsChanged() || !delta.readings().isEmpty();
            for (ServerPlayer player : watching) {
                current.add(player.getUUID());
                if (stale || !previous.contains(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, snapshot(channel, tick));
                } else if (changed) {
                    PacketDistributor.sendToPlayer(player, delta);
                }
            }
        }
        closeFor(level, channel, previous, current);
        channel.setObservers(current);
    }

    private static void closeFor(ServerLevel level, Channel channel, Set<UUID> previous, Set<UUID> current) {
        for (UUID former : previous) {
            ServerPlayer player = current.contains(former) ? null : level.getServer().getPlayerList().getPlayer(former);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new Payloads.ChannelClosed(channel.key()));
            }
        }
    }

    private static List<ServerPlayer> watchers(ServerLevel level, Channel channel) {
        List<ServerPlayer> watching = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            for (BlockPos panel : channel.panels()) {
                if (player.distanceToSqr(panel.getX() + 0.5, panel.getY() + 0.5, panel.getZ() + 0.5) <= VIEW_DISTANCE_SQUARED) {
                    watching.add(player);
                    break;
                }
            }
        }
        return watching;
    }

    private static Payloads.ChannelDelta delta(Channel channel, long tick) {
        List<Payloads.Reading> readings = new ArrayList<>();
        for (SensorEntry entry : channel.entries()) {
            if (entry.takeChange()) {
                readings.add(new Payloads.Reading(entry.id(), entry.value(), entry.capacity(), entry.alarmLevel()));
            }
        }
        boolean alarmsChanged = channel.alarms().takeChange();
        return new Payloads.ChannelDelta(channel.key(), channel.powered(tick), readings, alarmsChanged, alarmsChanged ? lines(channel) : List.of());
    }

    private static Payloads.ChannelSnapshot snapshot(Channel channel, long tick) {
        List<Payloads.SensorState> sensors = new ArrayList<>();
        for (SensorEntry entry : channel.entries()) {
            sensors.add(new Payloads.SensorState(entry.id(), entry.name(), entry.unit(), entry.icon(), entry.metricId(), entry.value(),
                    entry.capacity(),
                    entry.alarmLevel(), entry.series().seconds().toArray()));
        }
        return new Payloads.ChannelSnapshot(channel.key(), channel.powered(tick), sensors, lines(channel));
    }

    private static List<Payloads.AlarmLine> lines(Channel channel) {
        return channel.alarms().lines().stream().map(line -> new Payloads.AlarmLine(line.message(), line.severity(), line.active())).toList();
    }
}
