package dev.nostalgiacraft.monitor.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

public final class Owners {
    public interface Resolver {
        UUID ownerOf(ServerPlayer player);

        Set<UUID> members(MinecraftServer server, UUID owner);
    }

    private static final Resolver SOLO = new Resolver() {
        @Override
        public UUID ownerOf(ServerPlayer player) {
            return player.getUUID();
        }

        @Override
        public Set<UUID> members(MinecraftServer server, UUID owner) {
            return Set.of(owner);
        }
    };
    private static Resolver resolver = SOLO;

    private Owners() {
    }

    public static void use(Resolver teams) {
        resolver = teams;
    }

    public static UUID ownerOf(ServerPlayer player) {
        return resolver.ownerOf(player);
    }

    public static Set<UUID> members(MinecraftServer server, UUID owner) {
        return resolver.members(server, owner);
    }

    public static boolean belongs(ServerPlayer player, UUID owner) {
        return owner.equals(player.getUUID()) || owner.equals(ownerOf(player));
    }
}
