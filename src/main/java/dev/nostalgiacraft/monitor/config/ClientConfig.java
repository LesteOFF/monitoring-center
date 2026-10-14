package dev.nostalgiacraft.monitor.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PERFORMANCE_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PERFORMANCE_MODE = builder.comment("Replaces sparklines with bars and stops the alarm pulse of the Screen")
                .define("performanceMode", false);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
