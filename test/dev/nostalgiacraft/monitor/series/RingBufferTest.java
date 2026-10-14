package dev.nostalgiacraft.monitor.series;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferTest {
    @Test
    void overwritesTheOldestSampleWhenFull() {
        RingBuffer buffer = new RingBuffer(3);
        for (int value = 1; value <= 5; value++) {
            buffer.push(value);
        }
        assertEquals(3, buffer.size());
        assertArrayEquals(new float[]{3, 4, 5}, buffer.toArray());
        assertEquals(5f, buffer.last());
    }

    @Test
    void emptyBufferHasNoLastSample() {
        assertTrue(Float.isNaN(new RingBuffer(3).last()));
    }

    @Test
    void restoresFromAnArrayKeepingTheNewestSamples() {
        RingBuffer buffer = new RingBuffer(2);
        buffer.restore(new float[]{1, 2, 3});
        assertArrayEquals(new float[]{2, 3}, buffer.toArray());
    }
}
