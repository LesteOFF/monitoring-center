package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.series.RingBuffer;
import dev.nostalgiacraft.monitor.series.Series;
import net.minecraft.nbt.CompoundTag;

final class SeriesNbt {
    private static final String SECONDS = "seconds";
    private static final String MINUTES = "minutes_";
    private static final String HOURS = "hours_";
    private static final String MINUTE_PROGRESS = "minute_progress";
    private static final String HOUR_PROGRESS = "hour_progress";
    private static final String SUM = "sum";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String VALID = "valid";
    private static final String PENDING = "pending";

    private SeriesNbt() {
    }

    static CompoundTag save(Series series) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(SECONDS, bits(series.seconds()));
        for (Series.Stat stat : Series.Stat.values()) {
            tag.putIntArray(MINUTES + stat.name(), bits(series.minutes(stat)));
            tag.putIntArray(HOURS + stat.name(), bits(series.hours(stat)));
        }
        tag.put(MINUTE_PROGRESS, saved(series.minuteProgress()));
        tag.put(HOUR_PROGRESS, saved(series.hourProgress()));
        return tag;
    }

    private static CompoundTag saved(Series.Progress progress) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(SUM, progress.sum());
        tag.putFloat(MIN, progress.min());
        tag.putFloat(MAX, progress.max());
        tag.putInt(VALID, progress.valid());
        tag.putInt(PENDING, progress.pending());
        return tag;
    }

    private static Series.Progress progress(CompoundTag tag) {
        return new Series.Progress(tag.getDouble(SUM), tag.getFloat(MIN), tag.getFloat(MAX), tag.getInt(VALID), tag.getInt(PENDING));
    }

    static void load(Series series, CompoundTag tag) {
        series.seconds().restore(floats(tag.getIntArray(SECONDS)));
        for (Series.Stat stat : Series.Stat.values()) {
            series.minutes(stat).restore(floats(tag.getIntArray(MINUTES + stat.name())));
            series.hours(stat).restore(floats(tag.getIntArray(HOURS + stat.name())));
        }
        series.restoreProgress(progress(tag.getCompound(MINUTE_PROGRESS)), progress(tag.getCompound(HOUR_PROGRESS)));
    }

    private static int[] bits(RingBuffer buffer) {
        float[] samples = buffer.toArray();
        int[] bits = new int[samples.length];
        for (int index = 0; index < samples.length; index++) {
            bits[index] = Float.floatToRawIntBits(samples[index]);
        }
        return bits;
    }

    private static float[] floats(int[] bits) {
        float[] samples = new float[bits.length];
        for (int index = 0; index < bits.length; index++) {
            samples[index] = Float.intBitsToFloat(bits[index]);
        }
        return samples;
    }
}
