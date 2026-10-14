package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.metric.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AggregationTest {
    private static final List<Sample> READINGS = List.of(new Sample(10, 100), new Sample(30, 50), new Sample(20, 150));

    @Test
    void sumAddsValuesAndCapacities() {
        assertEquals(new Sample(60, 300), Aggregation.SUM.combine(READINGS));
    }

    @Test
    void averageDividesValuesAndCapacities() {
        assertEquals(new Sample(20, 100), Aggregation.AVERAGE.combine(READINGS));
    }

    @Test
    void minimumAndMaximumKeepTheCapacityOfTheChosenReading() {
        assertEquals(new Sample(10, 100), Aggregation.MINIMUM.combine(READINGS));
        assertEquals(new Sample(30, 50), Aggregation.MAXIMUM.combine(READINGS));
    }

    @Test
    void nothingToCombineIsNoSample() {
        assertNull(Aggregation.SUM.combine(List.of()));
    }
}
