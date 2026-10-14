package dev.nostalgiacraft.monitor.metric;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface MetricReader {
    @Nullable
    Sample read(ServerLevel level, SensorBlockEntity sensor);
}
