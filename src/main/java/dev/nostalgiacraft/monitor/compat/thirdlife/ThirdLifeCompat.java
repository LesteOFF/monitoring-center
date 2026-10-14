package dev.nostalgiacraft.monitor.compat.thirdlife;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.compat.OptionalEvents;
import dev.nostalgiacraft.monitor.data.Owners;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.mj.thirdlife.network.ThirdLifePlusModVariables;

import java.util.UUID;

public final class ThirdLifeCompat {
    public static final String MOD_ID = "third_life_plus";

    private ThirdLifeCompat() {
    }

    public static void register() {
        Metrics.area("team_lives", "", "heart", (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> fewestLives(level, sensor)));
    }

    private static Sample fewestLives(ServerLevel level, SensorBlockEntity sensor) {
        if (sensor.channel() == null) {
            return null;
        }
        double fewest = Double.NaN;
        for (UUID member : Owners.members(level.getServer(), sensor.channel().owner())) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(member);
            if (online != null) {
                double lives = online.getData(ThirdLifePlusModVariables.PLAYER_VARIABLES.get()).Lives;
                fewest = Double.isNaN(fewest) ? lives : Math.min(fewest, lives);
            }
        }
        return Double.isNaN(fewest) ? null : Sample.of(fewest);
    }
}
