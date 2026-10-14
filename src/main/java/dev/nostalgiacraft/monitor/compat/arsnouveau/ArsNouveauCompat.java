package dev.nostalgiacraft.monitor.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.compat.OptionalEvents;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import net.minecraft.server.level.ServerLevel;

public final class ArsNouveauCompat {
    public static final String MOD_ID = "ars_nouveau";

    private ArsNouveauCompat() {
    }

    public static void register() {
        Metrics.direct("ars_source", "", "star", (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> source(level, sensor)));
    }

    private static Sample source(ServerLevel level, SensorBlockEntity sensor) {
        ISourceCap capability = sensor.neighbor(CapabilityRegistry.SOURCE_CAPABILITY);
        if (capability != null) {
            return new Sample(capability.getSource(), capability.getSourceCapacity());
        }
        return level.getBlockEntity(sensor.targetPos()) instanceof ISourceTile tile ? new Sample(tile.getSource(), tile.getMaxSource()) : null;
    }
}
