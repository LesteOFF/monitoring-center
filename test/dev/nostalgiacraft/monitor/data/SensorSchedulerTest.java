package dev.nostalgiacraft.monitor.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensorSchedulerTest {
    private record Slot(int id, boolean area, boolean idle) implements SensorScheduler.Slot {
    }

    private static List<Slot> slots(int count, int areaEvery, boolean idle) {
        List<Slot> slots = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            slots.add(new Slot(id, areaEvery > 0 && id % areaEvery == 0, idle));
        }
        return slots;
    }

    @Test
    void sixtyFourSensorsNeverReadMoreThanFourPerTick() {
        List<Slot> slots = slots(64, 0, false);
        Map<Integer, Integer> reads = new HashMap<>();
        for (long tick = 0; tick < SensorScheduler.ACTIVE_PERIOD; tick++) {
            List<Slot> read = new ArrayList<>();
            SensorScheduler.run(tick, slots, read::add);
            assertTrue(read.size() <= 4, "tick " + tick + " read " + read.size());
            read.forEach(slot -> reads.merge(slot.id(), 1, Integer::sum));
        }
        assertEquals(64, reads.size());
        assertTrue(reads.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void areaSensorsNeverShareATick() {
        List<Slot> slots = slots(64, 4, false);
        Map<Integer, Integer> reads = new HashMap<>();
        for (long tick = 0; tick < SensorScheduler.SLOW_PERIOD; tick++) {
            List<Slot> read = new ArrayList<>();
            SensorScheduler.run(tick, slots, read::add);
            assertTrue(read.stream().filter(Slot::area).count() <= 1, "tick " + tick);
            read.stream().filter(Slot::area).forEach(slot -> reads.merge(slot.id(), 1, Integer::sum));
        }
        assertEquals(16, reads.size());
        assertTrue(reads.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void moreAreaSensorsThanTheSlowPeriodStillNeverShareATick() {
        List<Slot> slots = slots(150, 1, false);
        for (long tick = 0; tick < 300; tick++) {
            List<Slot> read = new ArrayList<>();
            SensorScheduler.run(tick, slots, read::add);
            assertTrue(read.size() <= 1, "tick " + tick);
        }
    }

    @Test
    void idleChannelsSampleOnceEverySlowPeriod() {
        List<Slot> slots = slots(8, 0, true);
        List<Slot> read = new ArrayList<>();
        for (long tick = 0; tick < SensorScheduler.SLOW_PERIOD; tick++) {
            SensorScheduler.run(tick, slots, read::add);
        }
        assertEquals(8, read.size());
    }
}
