package dev.nostalgiacraft.monitor.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.nostalgiacraft.monitor.compat.OptionalEvents;
import dev.nostalgiacraft.monitor.data.Owners;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

public final class FtbTeamsCompat {
    public static final String MOD_ID = "ftbteams";

    private FtbTeamsCompat() {
    }

    public static void register() {
        Owners.use(new Owners.Resolver() {
            @Override
            public UUID ownerOf(ServerPlayer player) {
                return OptionalEvents.guarded(MOD_ID, player.getUUID(), () -> teamOf(player));
            }

            @Override
            public Set<UUID> members(MinecraftServer server, UUID owner) {
                return OptionalEvents.guarded(MOD_ID, Set.of(owner), () -> membersOf(owner));
            }
        });
    }

    private static UUID teamOf(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return player.getUUID();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).map(Team::getTeamId).orElse(player.getUUID());
    }

    private static Set<UUID> membersOf(UUID owner) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Set.of(owner);
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(owner).map(Team::getMembers).orElse(Set.of(owner));
    }
}
