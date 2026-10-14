package dev.nostalgiacraft.monitor.panel;

import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueFormatTest {
    @Test
    void abbreviatesLargeNumbersWithACommaDecimal() {
        assertEquals("1,2 M", ValueFormat.number(1_200_000));
        assertEquals("1,5 k", ValueFormat.number(1500));
        assertEquals("-1,5 k", ValueFormat.number(-1500));
    }

    @Test
    void keepsSmallNumbersWhole() {
        assertEquals("64", ValueFormat.number(64));
        assertEquals("0,5", ValueFormat.number(0.5));
    }

    @Test
    void roundingNeverShowsFourDigitsBeforeTheSuffix() {
        assertEquals("1 k", ValueFormat.number(999.96));
    }

    @Test
    void missingSampleHasNoNumber() {
        assertEquals(ValueFormat.MISSING, ValueFormat.number(Double.NaN));
        assertEquals(ValueFormat.MISSING, ValueFormat.percent(5, 0));
    }

    @Test
    void durationsUseTheLargestSensibleUnit() {
        assertEquals("45 s", ValueFormat.duration(45));
        assertEquals("12 min", ValueFormat.duration(720));
        assertEquals("3 h", ValueFormat.duration(10800));
        assertEquals("2 d", ValueFormat.duration(172800));
        assertEquals(ValueFormat.MISSING, ValueFormat.duration(Double.NaN));
    }

    @Test
    void ratesCarryTheirSignAndUnit() {
        assertEquals("+40", ValueFormat.derived(Kind.RATE_TICK, 40));
        assertEquals("-1,5 k", ValueFormat.derived(Kind.RATE_SECOND, -1500));
        assertEquals("FE/t", ValueFormat.unit(Kind.RATE_TICK, "FE"));
        assertEquals("12 min", ValueFormat.derived(Kind.FORECAST, 720));
    }

    @Test
    void smallPercentagesKeepADecimalInsteadOfCollapsingToZero() {
        assertEquals("0%", ValueFormat.percent(0, 1000));
        assertEquals("<0,1%", ValueFormat.percent(185164, 2147483647d * 100));
        assertEquals("2,5%", ValueFormat.percent(25, 1000));
        assertEquals("5%", ValueFormat.percent(50, 1000));
        assertEquals("100%", ValueFormat.percent(1000, 1000));
    }

    @Test
    void percentIsRounded() {
        assertEquals("64%", ValueFormat.percent(639, 1000));
    }

    @Test
    void clockShowsHoursAndMinutes() {
        assertEquals("06:00", ValueFormat.clock(6));
        assertEquals("13:30", ValueFormat.clock(13.5));
        assertEquals(ValueFormat.MISSING, ValueFormat.clock(Double.NaN));
    }
}
