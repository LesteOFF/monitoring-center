package dev.nostalgiacraft.monitor.metric;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemFlowTest {
    private static final double EXACT = 1e-9;

    @Test
    void increasesAndDecreasesAreCountedApartPerKindOfItem() {
        ItemFlow<String> flow = new ItemFlow<>();
        flow.observe(Map.of("ore", 10L), 0);
        flow.observe(Map.of("ore", 14L), 1);
        flow.observe(Map.of("ore", 9L), 2);
        flow.observe(Map.of("ore", 12L, "ingot", 6L), 3);
        ItemFlow.Rates rates = flow.takeRates(20);
        assertEquals(13, rates.in(), EXACT);
        assertEquals(5, rates.out(), EXACT);
    }

    @Test
    void aMachineTurningOneItemIntoAnotherShowsBothDirections() {
        ItemFlow<String> flow = new ItemFlow<>();
        flow.observe(Map.of("ore", 8L, "dust", 0L), 0);
        flow.observe(Map.of("ore", 7L, "dust", 1L), 1);
        flow.observe(Map.of("dust", 2L), 2);
        ItemFlow.Rates rates = flow.takeRates(20);
        assertEquals(2, rates.in(), EXACT);
        assertEquals(8, rates.out(), EXACT);
    }

    @Test
    void aBurstIsAveragedOverTheLastReadingsInsteadOfFlickering() {
        ItemFlow<String> flow = new ItemFlow<>();
        flow.observe(Map.of("ingot", 500L), 0);
        assertEquals(0, flow.takeRates(20).in(), EXACT);
        flow.observe(Map.of("ingot", 540L), 30);
        assertEquals(20, flow.takeRates(40).in(), EXACT);
        assertEquals(40 / 3.0, flow.takeRates(60).in(), EXACT);
        for (int tick = 80; tick <= 160; tick += 20) {
            flow.takeRates(tick);
        }
        assertEquals(0, flow.takeRates(180).in(), EXACT);
    }

    @Test
    void aResetForgetsThePreviousCount() {
        ItemFlow<String> flow = new ItemFlow<>();
        flow.observe(Map.of("ingot", 5L), 0);
        flow.reset();
        flow.observe(Map.of("ingot", 900L), 1);
        ItemFlow.Rates rates = flow.takeRates(21);
        assertEquals(0, rates.in(), EXACT);
        assertEquals(0, rates.out(), EXACT);
    }
}
