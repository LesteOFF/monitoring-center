package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.ChannelKey;
import org.jetbrains.annotations.Nullable;

public interface ChannelMember {
    @Nullable
    ChannelKey channel();

    boolean joinChannel(ChannelKey channel);
}
