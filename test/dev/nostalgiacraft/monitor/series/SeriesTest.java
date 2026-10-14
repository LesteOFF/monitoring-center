package dev.nostalgiacraft.monitor.series;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesTest {
    @Test
    void sixtySecondsCloseAMinuteWithAverageMinimumAndMaximum() {
        Series series = new Series();
        for (int second = 1; second <= 60; second++) {
            series.push(second);
        }
        assertEquals(1, series.minutes(Series.Stat.AVG).size());
        assertEquals(30.5f, series.minutes(Series.Stat.AVG).last());
        assertEquals(1f, series.minutes(Series.Stat.MIN).last());
        assertEquals(60f, series.minutes(Series.Stat.MAX).last());
    }

    @Test
    void sixtyMinutesCloseAnHour() {
        Series series = new Series();
        for (int second = 0; second < 3600; second++) {
            series.push(second < 1800 ? 10 : 20);
        }
        assertEquals(1, series.hours(Series.Stat.AVG).size());
        assertEquals(15f, series.hours(Series.Stat.AVG).last());
        assertEquals(10f, series.hours(Series.Stat.MIN).last());
        assertEquals(20f, series.hours(Series.Stat.MAX).last());
    }

    @Test
    void gapsAreIgnoredByTheAggregation() {
        Series series = new Series();
        for (int second = 0; second < 60; second++) {
            series.push(second < 30 ? Float.NaN : 8);
        }
        assertEquals(8f, series.minutes(Series.Stat.AVG).last());
    }

    @Test
    void aMinuteMadeOnlyOfGapsIsAGap() {
        Series series = new Series();
        for (int second = 0; second < 60; second++) {
            series.push(Float.NaN);
        }
        assertTrue(Float.isNaN(series.minutes(Series.Stat.AVG).last()));
    }

    @Test
    void secondsKeepOnlyTheLastFiveMinutes() {
        Series series = new Series();
        for (int second = 0; second < 400; second++) {
            series.push(second);
        }
        assertEquals(Series.SECONDS, series.seconds().size());
        assertEquals(399f, series.seconds().last());
    }

    @Test
    void anOpenMinuteSurvivesBeingSavedAndRestored() {
        Series saved = new Series();
        for (int second = 1; second <= 30; second++) {
            saved.push(second);
        }
        Series restored = new Series();
        restored.restoreProgress(saved.minuteProgress(), saved.hourProgress());
        for (int second = 31; second <= 60; second++) {
            restored.push(second);
        }
        assertEquals(30.5f, restored.minutes(Series.Stat.AVG).last());
        assertEquals(1f, restored.minutes(Series.Stat.MIN).last());
        assertEquals(60f, restored.minutes(Series.Stat.MAX).last());
    }
}
