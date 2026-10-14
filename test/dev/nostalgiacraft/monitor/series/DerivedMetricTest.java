package dev.nostalgiacraft.monitor.series;

import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedMetricTest {
    private static final double TOLERANCE = 1e-6;

    private static Series rising(int seconds, float step) {
        Series series = new Series();
        for (int second = 0; second < seconds; second++) {
            series.push(second * step);
        }
        return series;
    }

    @Test
    void rateIsTheChangePerSecondOfAKnownSeries() {
        assertEquals(5.0, DerivedMetric.rate(rising(60, 5).seconds(), 10), TOLERANCE);
    }

    @Test
    void rateNeedsEnoughSamples() {
        assertTrue(Double.isNaN(DerivedMetric.rate(rising(5, 5).seconds(), 10)));
    }

    @Test
    void rateScalesToTickAndMinute() {
        Series series = rising(60, 5);
        assertEquals(0.25, DerivedMetric.compute(Kind.RATE_TICK, Period.FIVE_MINUTES, series, 295, 0), TOLERANCE);
        assertEquals(300.0, DerivedMetric.compute(Kind.RATE_MINUTE, Period.FIVE_MINUTES, series, 295, 0), TOLERANCE);
    }

    @Test
    void forecastIsTheTimeToEmptyWhenFalling() {
        assertEquals(20.0, DerivedMetric.forecastSeconds(100, 1000, -5), TOLERANCE);
    }

    @Test
    void forecastIsTheTimeToFillWhenRising() {
        assertEquals(180.0, DerivedMetric.forecastSeconds(100, 1000, 5), TOLERANCE);
    }

    @Test
    void nothingIsForecastWhenTheRateIsZero() {
        assertTrue(Double.isNaN(DerivedMetric.forecastSeconds(100, 1000, 0)));
    }

    @Test
    void fillingCannotBeForecastWithoutACapacity() {
        assertTrue(Double.isNaN(DerivedMetric.forecastSeconds(100, 0, 5)));
    }

    @Test
    void minimumMaximumAndAverageIgnoreGaps() {
        RingBuffer buffer = new RingBuffer(8);
        for (float sample : new float[]{4, Float.NaN, 10, 1}) {
            buffer.push(sample);
        }
        assertEquals(1.0, DerivedMetric.minimum(buffer), TOLERANCE);
        assertEquals(10.0, DerivedMetric.maximum(buffer), TOLERANCE);
        assertEquals(5.0, DerivedMetric.average(buffer), TOLERANCE);
    }

    @Test
    void statisticsOfAnEmptyPeriodAreMissing() {
        assertTrue(Double.isNaN(DerivedMetric.average(new RingBuffer(4))));
    }

    @Test
    void longPeriodsReadTheAggregatedTiers() {
        Series series = new Series();
        for (int second = 0; second < 120; second++) {
            series.push(second < 60 ? 10 : 30);
        }
        assertEquals(10.0, DerivedMetric.compute(Kind.MINIMUM, Period.SIX_HOURS, series, 30, 0), TOLERANCE);
        assertEquals(30.0, DerivedMetric.compute(Kind.MAXIMUM, Period.SIX_HOURS, series, 30, 0), TOLERANCE);
        assertEquals(20.0, DerivedMetric.compute(Kind.AVERAGE, Period.SIX_HOURS, series, 30, 0), TOLERANCE);
    }

    @Test
    void percentNeedsACapacity() {
        Series series = new Series();
        assertEquals(25.0, DerivedMetric.compute(Kind.PERCENT, Period.FIVE_MINUTES, series, 50, 200), TOLERANCE);
        assertTrue(Double.isNaN(DerivedMetric.compute(Kind.PERCENT, Period.FIVE_MINUTES, series, 50, 0)));
    }
}
