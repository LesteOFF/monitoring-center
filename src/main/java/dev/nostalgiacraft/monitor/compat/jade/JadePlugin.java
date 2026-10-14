package dev.nostalgiacraft.monitor.compat.jade;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.CentralBlock;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.block.ChannelAccess;
import dev.nostalgiacraft.monitor.block.ChannelMember;
import dev.nostalgiacraft.monitor.block.ScreenBlock;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.SensorBlock;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import dev.nostalgiacraft.monitor.panel.ValueFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID = MonitoringCenter.id("sensor");
    private static final String NAME = "monitor_name";
    private static final String CHANNEL = "monitor_channel";
    private static final String READING = "monitor_reading";
    private static final String ALARMS = "monitor_alarms";
    private static final ResourceLocation CHANNEL_UID = MonitoringCenter.id("channel");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new SensorReading(), SensorBlockEntity.class);
        registration.registerBlockDataProvider(new ChannelStatus(), CentralBlockEntity.class);
        registration.registerBlockDataProvider(new ChannelStatus(), ScreenBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new SensorReading(), SensorBlock.class);
        registration.registerBlockComponent(new ChannelStatus(), CentralBlock.class);
        registration.registerBlockComponent(new ChannelStatus(), ScreenBlock.class);
    }

    private static final class ChannelStatus implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            ChannelMember member = ChannelAccess.member(accessor.getLevel(), accessor.getPosition());
            if (member == null || member.channel() == null || !(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            Channel channel = MonitorNetwork.get(level).find(member.channel());
            data.putString(CHANNEL, member.channel().name());
            data.putInt(ALARMS, channel == null ? 0 : channel.alarms().activeCount());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains(CHANNEL)) {
                tooltip.add(Component.translatable("jade.monitoringcenter.channel", data.getString(CHANNEL)).withStyle(ChatFormatting.GRAY));
            }
            if (data.getInt(ALARMS) > 0) {
                tooltip.add(Component.translatable("jade.monitoringcenter.alarms", data.getInt(ALARMS)).withStyle(ChatFormatting.RED));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return CHANNEL_UID;
        }
    }

    private static final class SensorReading implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SensorBlockEntity sensor) || !(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            Channel channel = MonitorNetwork.get(level).find(sensor.channel());
            SensorEntry entry = channel == null ? null : channel.sensorAt(sensor.getBlockPos());
            if (entry != null) {
                data.putString(NAME, entry.name());
                data.putString(CHANNEL, channel.key().name());
                data.putString(READING, reading(entry));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains(READING)) {
                tooltip.add(Component.literal(data.getString(NAME) + ": " + data.getString(READING)).withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.translatable("jade.monitoringcenter.channel", data.getString(CHANNEL)).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        private static String reading(SensorEntry entry) {
            String value = (ValueFormat.number(entry.value()) + " " + entry.unit()).strip();
            return entry.capacity() > 0 ? value + " (" + ValueFormat.percent(entry.value(), entry.capacity()) + ")" : value;
        }
    }
}
