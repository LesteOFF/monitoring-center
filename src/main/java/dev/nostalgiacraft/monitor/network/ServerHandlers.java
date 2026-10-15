package dev.nostalgiacraft.monitor.network;

import dev.nostalgiacraft.monitor.block.ActuatorBlockEntity;
import dev.nostalgiacraft.monitor.block.ChannelAccess;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.block.ChannelMember;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.ActuatorSettings;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.data.CentralConfig;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.PanelSync;
import dev.nostalgiacraft.monitor.data.Owners;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

final class ServerHandlers {
    private static final int LIMIT_WINDOW_TICKS = 20;
    private static final int PACKETS_PER_WINDOW = 40;
    private static final RateLimit LIMIT = new RateLimit(LIMIT_WINDOW_TICKS, PACKETS_PER_WINDOW);

    private ServerHandlers() {
    }

    static <T extends CustomPacketPayload> IPayloadHandler<T> limited(IPayloadHandler<T> handler) {
        return (message, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (LIMIT.allow(player.getUUID(), player.server.getTickCount())) {
                handler.handle(message, context);
            }
        };
    }

    static void forget(UUID player) {
        LIMIT.forget(player);
    }

    static void configureSensor(Payloads.ConfigureSensor message, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (editable(player, message.pos()) instanceof SensorBlockEntity sensor) {
            sensor.configure(message.name(), message.metric(), message.tank(), message.radius());
            rename(player, sensor, message.channel());
        }
    }

    static void setSensorFilter(Payloads.SetSensorFilter message, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (editable(player, message.pos()) instanceof SensorBlockEntity sensor) {
            sensor.setFilter(message.fromHand() ? player.getMainHandItem() : ItemStack.EMPTY);
        }
    }

    static void setSensorFilterTag(Payloads.SetSensorFilterTag message, IPayloadContext context) {
        if (editable((ServerPlayer) context.player(), message.pos()) instanceof SensorBlockEntity sensor) {
            sensor.setFilterTag(message.tag());
        }
    }

    static void configurePanel(Payloads.ConfigurePanel message, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (editable(player, message.pos()) instanceof ScreenBlockEntity screen) {
            screen.clearLinkLines();
            screen.setConfig(PanelConfig.fromTag(message.config()));
            rename(player, screen, message.channel());
        }
    }

    static void configureCentral(Payloads.ConfigureCentral message, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (editable(player, message.pos()) instanceof CentralBlockEntity central) {
            MonitorNetwork network = MonitorNetwork.get(player.serverLevel());
            Channel edited = network.find(central.channel());
            rename(player, central, message.channel());
            Channel target = edited != null ? edited : unconfigured(network.find(central.channel()));
            if (target != null) {
                CentralConfig.apply(target, message.config());
                network.setDirty();
            }
        }
    }

    static void configureActuator(Payloads.ConfigureActuator message, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (editable(player, message.pos()) instanceof ActuatorBlockEntity actuator) {
            rename(player, actuator, message.channel());
            actuator.configure(ActuatorSettings.fromTag(message.settings()));
        }
    }

    @Nullable
    private static Channel unconfigured(@Nullable Channel joined) {
        return joined != null && joined.alarms().rules().isEmpty() && joined.sums().isEmpty() ? joined : null;
    }

    static void requestHistory(Payloads.RequestHistory message, IPayloadContext context) {
        PanelSync.sendHistory((ServerPlayer) context.player(), message.key(), message.sensorId());
    }

    @Nullable
    static ChannelMember editable(ServerPlayer player, BlockPos pos) {
        if (!ChannelAccess.reachable(player, pos)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof ChannelMember member && ChannelAccess.allowed(player, member.channel())
                ? member : null;
    }

    static void rename(ServerPlayer player, ChannelMember member, String channelName) {
        ChannelKey current = member.channel();
        ChannelKey wanted = new ChannelKey(current == null ? Owners.ownerOf(player) : current.owner(), channelName);
        if (!wanted.equals(current) && !member.joinChannel(wanted)) {
            String reason = member instanceof CentralBlockEntity ? "central_exists" : "channel_full";
            player.displayClientMessage(Component.translatable("message.monitoringcenter." + reason, wanted.name()), true);
        }
    }
}
