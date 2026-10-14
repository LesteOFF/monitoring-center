package dev.nostalgiacraft.monitor.series;

public final class Series {
    public static final int SECONDS = 300;
    public static final int MINUTES = 360;
    public static final int HOURS = 168;
    private static final int SAMPLES_PER_BUCKET = 60;

    public enum Stat {
        AVG, MIN, MAX
    }

    public record Progress(double sum, float min, float max, int valid, int pending) {
    }

    private final RingBuffer seconds = new RingBuffer(SECONDS);
    private final Tier minutes = new Tier(MINUTES);
    private final Tier hours = new Tier(HOURS);

    public void push(float sample) {
        seconds.push(sample);
        if (minutes.accumulate(sample, sample, sample)) {
            hours.accumulate(minutes.buffer(Stat.AVG).last(), minutes.buffer(Stat.MIN).last(), minutes.buffer(Stat.MAX).last());
        }
    }

    public RingBuffer seconds() {
        return seconds;
    }

    public RingBuffer minutes(Stat stat) {
        return minutes.buffer(stat);
    }

    public RingBuffer hours(Stat stat) {
        return hours.buffer(stat);
    }

    public Progress minuteProgress() {
        return minutes.progress();
    }

    public Progress hourProgress() {
        return hours.progress();
    }

    public void restoreProgress(Progress minuteProgress, Progress hourProgress) {
        minutes.restore(minuteProgress);
        hours.restore(hourProgress);
    }

    private static final class Tier {
        private final RingBuffer[] buffers = new RingBuffer[Stat.values().length];
        private double sum;
        private float min;
        private float max;
        private int valid;
        private int pending;

        private Tier(int capacity) {
            for (int index = 0; index < buffers.length; index++) {
                buffers[index] = new RingBuffer(capacity);
            }
        }

        private RingBuffer buffer(Stat stat) {
            return buffers[stat.ordinal()];
        }

        private Progress progress() {
            return new Progress(sum, min, max, valid, pending);
        }

        private void restore(Progress saved) {
            pending = Math.clamp(saved.pending(), 0, SAMPLES_PER_BUCKET - 1);
            valid = Double.isFinite(saved.sum()) ? Math.clamp(saved.valid(), 0, pending) : 0;
            sum = valid == 0 ? 0 : saved.sum();
            min = saved.min();
            max = saved.max();
        }

        private boolean accumulate(float average, float lowest, float highest) {
            if (!Float.isNaN(average)) {
                sum += average;
                min = valid == 0 ? lowest : Math.min(min, lowest);
                max = valid == 0 ? highest : Math.max(max, highest);
                valid++;
            }
            if (++pending < SAMPLES_PER_BUCKET) {
                return false;
            }
            close();
            return true;
        }

        private void close() {
            buffer(Stat.AVG).push(valid == 0 ? Float.NaN : (float) (sum / valid));
            buffer(Stat.MIN).push(valid == 0 ? Float.NaN : min);
            buffer(Stat.MAX).push(valid == 0 ? Float.NaN : max);
            sum = 0;
            valid = 0;
            pending = 0;
        }
    }
}
