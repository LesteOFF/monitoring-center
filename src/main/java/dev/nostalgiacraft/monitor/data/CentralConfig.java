package dev.nostalgiacraft.monitor.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class CentralConfig {
    private static final String UNLINKED = "unlinked";
    public static final String SUMS = "sums";

    private CentralConfig() {
    }

    public static CompoundTag unlinked() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(UNLINKED, true);
        return tag;
    }

    public static boolean isUnlinked(CompoundTag tag) {
        return tag.getBoolean(UNLINKED);
    }

    public static CompoundTag of(Channel channel) {
        CompoundTag tag = channel.alarms().toTag();
        tag.put(SUMS, SumSpec.toTag(channel.sumSpecs()));
        return tag;
    }

    public static void apply(Channel channel, CompoundTag tag) {
        channel.replaceSums(SumSpec.fromTag(tag.getList(SUMS, Tag.TAG_COMPOUND)));
        channel.alarms().apply(tag);
    }
}
