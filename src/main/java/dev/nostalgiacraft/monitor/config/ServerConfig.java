package dev.nostalgiacraft.monitor.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_SENSORS_PER_CHANNEL;
    public static final ModConfigSpec.IntValue AREA_MAX_RADIUS;
    public static final ModConfigSpec.IntValue CENTRAL_FE_PER_TICK;
    public static final ModConfigSpec.IntValue BATTERY_SECONDS;
    public static final ModConfigSpec.IntValue NOTICE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue ALARM_HYSTERESIS_PERCENT;
    public static final ModConfigSpec.IntValue COUPLER_MIN_RPM;
    public static final ModConfigSpec.DoubleValue COUPLER_STRESS_IMPACT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_SENSORS_PER_CHANNEL = builder.comment("Sensors a single channel accepts").defineInRange("maxSensorsPerChannel", 64, 1, 256);
        AREA_MAX_RADIUS = builder.comment("Largest radius, in blocks, of an area sensor").defineInRange("areaMaxRadius", 8, 1, 16);
        CENTRAL_FE_PER_TICK = builder.comment("Fixed FE per tick a Central spends to keep its channel on")
                .defineInRange("centralFePerTick", 40, 0, 100000);
        BATTERY_SECONDS = builder.comment("Seconds the internal battery of a Central holds the channel without input")
                .defineInRange("batterySeconds", 5, 1, 60);
        NOTICE_COOLDOWN_SECONDS = builder.comment("Minimum seconds between two notices of the same alarm or of the same Central going off")
                .defineInRange("noticeCooldownSeconds", 300, 0, 86400);
        ALARM_HYSTERESIS_PERCENT = builder.comment("Margin, in percent of the threshold, a value must return by before an alarm ends")
                .defineInRange("alarmHysteresisPercent", 5.0, 0.0, 50.0);
        COUPLER_MIN_RPM = builder.comment("Slowest rotation at which a Kinetic Coupler keeps its Central on")
                .defineInRange("couplerMinRpm", 32, 1, 256);
        COUPLER_STRESS_IMPACT = builder.comment("Stress impact of a Kinetic Coupler, in SU per RPM")
                .defineInRange("couplerStressImpact", 4.0, 0.0, 1024.0);
        SPEC = builder.build();
    }

    private ServerConfig() {
    }
}
