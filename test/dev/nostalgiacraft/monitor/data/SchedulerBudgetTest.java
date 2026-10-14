package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.series.Series;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerBudgetTest {
    private static final int SENSORS = 64;
    private static final int WARM_UP_TICKS = 20_000;
    private static final int MEASURED_TICKS = 200_000;
    private static final double BUDGET_MILLIS_PER_TICK = 0.05;
    private static final double NANOS_PER_MILLI = 1e6;

    private record Slot(Series series) implements SensorScheduler.Slot {
        @Override
        public boolean area() {
            return false;
        }

        @Override
        public boolean idle() {
            return false;
        }
    }

    private static void run(List<Slot> slots, int ticks) {
        for (long tick = 0; tick < ticks; tick++) {
            long sampled = tick;
            SensorScheduler.run(tick, slots, slot -> slot.series().push(sampled));
        }
    }

    @Test
    void schedulingAndStoringSixtyFourSensorsStaysFarBelowTheTickBudget() {
        List<Slot> slots = new ArrayList<>();
        for (int sensor = 0; sensor < SENSORS; sensor++) {
            slots.add(new Slot(new Series()));
        }
        run(slots, WARM_UP_TICKS);
        long started = System.nanoTime();
        run(slots, MEASURED_TICKS);
        double millisPerTick = (System.nanoTime() - started) / NANOS_PER_MILLI / MEASURED_TICKS;
        assertTrue(millisPerTick < BUDGET_MILLIS_PER_TICK, "scheduler cost " + millisPerTick + " ms per tick");
    }
}
