package dev.nostalgiacraft.monitor.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import dev.nostalgiacraft.monitor.panel.ValueFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public final class SensorDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof SensorBlockEntity sensor) || !(context.level() instanceof ServerLevel level)) {
            return EMPTY;
        }
        Channel channel = MonitorNetwork.get(level).find(sensor.channel());
        SensorEntry entry = channel == null ? null : channel.sensorAt(sensor.getBlockPos());
        if (entry == null) {
            return EMPTY;
        }
        String reading = (ValueFormat.number(entry.value()) + " " + entry.unit()).strip();
        return List.of(Component.literal(entry.name() + ": " + reading));
    }
}
