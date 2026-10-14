package dev.nostalgiacraft.monitor.data;

import java.util.List;
import java.util.function.Consumer;

public final class SensorScheduler {
    public static final int ACTIVE_PERIOD = 20;
    public static final int SLOW_PERIOD = 100;

    public interface Slot {
        boolean area();

        boolean idle();
    }

    private SensorScheduler() {
    }

    public static <S extends Slot> void run(long tick, List<S> slots, Consumer<S> reader) {
        int areaPeriod = Math.max(SLOW_PERIOD, areaCount(slots));
        int areaOrdinal = 0;
        for (int index = 0; index < slots.size(); index++) {
            S slot = slots.get(index);
            boolean due = slot.area() ? tick % areaPeriod == areaOrdinal++ : tick % period(slot) == index % period(slot);
            if (due) {
                reader.accept(slot);
            }
        }
    }

    public static int secondsPerSample(Slot slot) {
        return slot.area() || slot.idle() ? SLOW_PERIOD / ACTIVE_PERIOD : 1;
    }

    private static int period(Slot slot) {
        return slot.idle() ? SLOW_PERIOD : ACTIVE_PERIOD;
    }

    private static <S extends Slot> int areaCount(List<S> slots) {
        int count = 0;
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).area()) {
                count++;
            }
        }
        return count;
    }
}
