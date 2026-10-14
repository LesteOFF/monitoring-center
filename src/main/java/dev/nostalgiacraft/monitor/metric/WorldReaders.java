package dev.nostalgiacraft.monitor.metric;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.Owners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.UUID;

final class WorldReaders {
    private static final int TICKS_PER_HOUR = 1000;
    private static final int HOURS_PER_DAY = 24;
    private static final int DAWN_HOUR = 6;
    private static final int MAX_LIGHT = 15;
    private static final int THUNDER = 2;
    private static final double NANOS_PER_SECOND = 1e9;
    private static final double NANOS_PER_MILLI = 1e6;
    private static final int CROP_HEIGHT = 2;
    private static final int PERCENT = 100;

    private WorldReaders() {
    }

    static void register() {
        Metrics.area(Metrics.TIME, "h", "clock", WorldReaders::time);
        Metrics.area("weather", "", "cloud", WorldReaders::weather);
        Metrics.area("light", "", "sun", WorldReaders::light);
        Metrics.area("tps", "TPS", "pulse", WorldReaders::tps);
        Metrics.area("mspt", "ms", "server", WorldReaders::mspt);
        Metrics.area("players", "", "person", WorldReaders::players);
        Metrics.area("team_online", "", "person", WorldReaders::teamOnline);
        Metrics.area("mobs", "", "skull", WorldReaders::mobs);
        Metrics.area("ground_items", "", "pile", WorldReaders::groundItems);
        Metrics.area("crops", "%", "leaf", WorldReaders::crops);
        Metrics.area("animals", "", "heart", WorldReaders::animals);
        Metrics.area("players_near", "", "person", WorldReaders::playersNear);
        Metrics.area("xp", "", "star", WorldReaders::experience);
        Metrics.filtered("animals");
        Metrics.filtered("mobs");
        Metrics.filtered("ground_items");
    }

    private static Sample time(ServerLevel level, SensorBlockEntity sensor) {
        double hours = level.getDayTime() % (HOURS_PER_DAY * TICKS_PER_HOUR) / (double) TICKS_PER_HOUR;
        return new Sample((hours + DAWN_HOUR) % HOURS_PER_DAY, HOURS_PER_DAY);
    }

    private static Sample weather(ServerLevel level, SensorBlockEntity sensor) {
        return new Sample(level.isThundering() ? THUNDER : level.isRaining() ? 1 : 0, THUNDER);
    }

    private static Sample light(ServerLevel level, SensorBlockEntity sensor) {
        return new Sample(level.getMaxLocalRawBrightness(sensor.getBlockPos().above()), MAX_LIGHT);
    }

    private static Sample tps(ServerLevel level, SensorBlockEntity sensor) {
        MinecraftServer server = level.getServer();
        double target = server.tickRateManager().tickrate();
        double measured = NANOS_PER_SECOND / Math.max(1, server.getAverageTickTimeNanos());
        return new Sample(Math.min(target, measured), target);
    }

    private static Sample mspt(ServerLevel level, SensorBlockEntity sensor) {
        return Sample.of(level.getServer().getAverageTickTimeNanos() / NANOS_PER_MILLI);
    }

    private static Sample players(ServerLevel level, SensorBlockEntity sensor) {
        return new Sample(level.getServer().getPlayerCount(), level.getServer().getMaxPlayers());
    }

    private static Sample teamOnline(ServerLevel level, SensorBlockEntity sensor) {
        if (sensor.channel() == null) {
            return null;
        }
        Set<UUID> members = Owners.members(level.getServer(), sensor.channel().owner());
        long online = members.stream().filter(member -> level.getServer().getPlayerList().getPlayer(member) != null).count();
        return new Sample(online, members.size());
    }

    private static Sample mobs(ServerLevel level, SensorBlockEntity sensor) {
        return Sample.of(count(level, sensor, Mob.class));
    }

    private static Sample animals(ServerLevel level, SensorBlockEntity sensor) {
        return Sample.of(count(level, sensor, Animal.class));
    }

    private static int count(ServerLevel level, SensorBlockEntity sensor, Class<? extends Mob> kind) {
        EntityType<?> wanted = sensor.filter().getItem() instanceof SpawnEggItem egg ? egg.getType(sensor.filter()) : null;
        return level.getEntitiesOfClass(kind, area(sensor), mob -> wanted == null || mob.getType() == wanted).size();
    }

    private static Sample playersNear(ServerLevel level, SensorBlockEntity sensor) {
        return Sample.of(level.getEntitiesOfClass(Player.class, area(sensor), player -> !player.isSpectator()).size());
    }

    private static Sample experience(ServerLevel level, SensorBlockEntity sensor) {
        long total = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area(sensor))) {
            total += orb.getValue();
        }
        return Sample.of(total);
    }

    private static Sample groundItems(ServerLevel level, SensorBlockEntity sensor) {
        ItemStack filter = sensor.filter();
        long total = 0;
        for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, area(sensor),
                dropped -> filter.isEmpty() || ItemStack.isSameItem(dropped.getItem(), filter))) {
            total += dropped.getItem().getCount();
        }
        return Sample.of(total);
    }

    private static Sample crops(ServerLevel level, SensorBlockEntity sensor) {
        BlockPos center = sensor.getBlockPos();
        int radius = sensor.radius();
        int total = 0;
        int mature = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -CROP_HEIGHT, -radius), center.offset(radius, CROP_HEIGHT, radius))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            int ripeness = ripeness(level.getBlockState(pos));
            if (ripeness >= 0) {
                total++;
                mature += ripeness;
            }
        }
        return new Sample(total == 0 ? 0 : mature * (double) PERCENT / total, PERCENT);
    }

    private static int ripeness(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? 1 : 0;
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? 1 : 0;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE ? 1 : 0;
        }
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE ? 1 : 0;
        }
        return -1;
    }

    private static AABB area(SensorBlockEntity sensor) {
        return new AABB(sensor.getBlockPos()).inflate(sensor.radius());
    }
}
