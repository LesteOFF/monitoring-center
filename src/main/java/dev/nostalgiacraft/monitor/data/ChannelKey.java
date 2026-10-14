package dev.nostalgiacraft.monitor.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record ChannelKey(UUID owner, String name) {
    public static final String DEFAULT_NAME = "principal";
    public static final int MAX_NAME_LENGTH = 24;
    public static final StreamCodec<ByteBuf, ChannelKey> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ChannelKey::owner, ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), ChannelKey::name, ChannelKey::new);
    private static final String OWNER = "owner";
    private static final String NAME = "channel";

    public ChannelKey {
        name = sanitize(name);
    }

    public ChannelKey renamed(String newName) {
        return new ChannelKey(owner, newName);
    }

    public void save(CompoundTag tag) {
        tag.putUUID(OWNER, owner);
        tag.putString(NAME, name);
    }

    public static ChannelKey load(CompoundTag tag) {
        return tag.hasUUID(OWNER) ? new ChannelKey(tag.getUUID(OWNER), tag.getString(NAME)) : null;
    }

    private static String sanitize(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return DEFAULT_NAME;
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
