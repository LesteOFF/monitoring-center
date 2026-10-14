package dev.nostalgiacraft.monitor.alarm;

import dev.nostalgiacraft.monitor.alarm.AlarmRule.Comparison;
import dev.nostalgiacraft.monitor.alarm.AlarmRule.Severity;
import dev.nostalgiacraft.monitor.alarm.AlarmState.Transition;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Period;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlarmStateTest {
    private static final double HYSTERESIS = 0.05;
    private static final AlarmRule BELOW_TWENTY = new AlarmRule(1, 7, Kind.VALUE, Comparison.BELOW, 20, Severity.WARNING, "low");
    private static final AlarmRule ABOVE_HUNDRED = new AlarmRule(2, 7, Kind.VALUE, Comparison.ABOVE, 100, Severity.CRITICAL, "high");

    @Test
    void raisesWhenTheValueCrossesTheThreshold() {
        AlarmState state = new AlarmState();
        assertEquals(Transition.NONE, state.update(BELOW_TWENTY, 25, HYSTERESIS));
        assertEquals(Transition.RAISED, state.update(BELOW_TWENTY, 19, HYSTERESIS));
        assertTrue(state.active());
    }

    @Test
    void doesNotRaiseTwiceWithoutComingBackThroughTheMargin() {
        AlarmState state = new AlarmState();
        state.update(BELOW_TWENTY, 19, HYSTERESIS);
        assertEquals(Transition.NONE, state.update(BELOW_TWENTY, 20.5, HYSTERESIS));
        assertEquals(Transition.NONE, state.update(BELOW_TWENTY, 19, HYSTERESIS));
        assertTrue(state.active());
    }

    @Test
    void clearsOnlyBeyondTheMarginAndCanRaiseAgain() {
        AlarmState state = new AlarmState();
        state.update(BELOW_TWENTY, 19, HYSTERESIS);
        assertEquals(Transition.CLEARED, state.update(BELOW_TWENTY, 21.5, HYSTERESIS));
        assertFalse(state.active());
        assertEquals(Transition.RAISED, state.update(BELOW_TWENTY, 19, HYSTERESIS));
    }

    @Test
    void aboveRulesMirrorTheMargin() {
        AlarmState state = new AlarmState();
        assertEquals(Transition.RAISED, state.update(ABOVE_HUNDRED, 101, HYSTERESIS));
        assertEquals(Transition.NONE, state.update(ABOVE_HUNDRED, 96, HYSTERESIS));
        assertEquals(Transition.CLEARED, state.update(ABOVE_HUNDRED, 94, HYSTERESIS));
    }

    @Test
    void aMissingSampleKeepsTheState() {
        AlarmState state = new AlarmState();
        state.update(BELOW_TWENTY, 19, HYSTERESIS);
        assertEquals(Transition.NONE, state.update(BELOW_TWENTY, Double.NaN, HYSTERESIS));
        assertTrue(state.active());
    }

    @Test
    void aZeroThresholdStillHasAMargin() {
        AlarmRule aboveZero = new AlarmRule(3, 7, Kind.VALUE, Comparison.ABOVE, 0, Severity.INFO, "any");
        AlarmState state = new AlarmState();
        assertEquals(Transition.RAISED, state.update(aboveZero, 1, HYSTERESIS));
        assertEquals(Transition.NONE, state.update(aboveZero, 0, HYSTERESIS));
        assertEquals(Transition.CLEARED, state.update(aboveZero, -1, HYSTERESIS));
    }

    @Test
    void anAlarmRestoredAsActiveStillAnnouncesItsEnd() {
        AlarmState state = new AlarmState();
        state.restore(true);
        assertEquals(Transition.CLEARED, state.update(BELOW_TWENTY, 30, HYSTERESIS));
        assertTrue(state.takeClearNotice());
    }

    @Test
    void aNoticeDroppedByTheCooldownIsStillPending() {
        AlarmState state = new AlarmState();
        state.takeNotice(1000, 6000);
        state.takeClearNotice();
        assertFalse(state.takeNotice(2000, 6000));
        assertFalse(state.noticed());
        assertTrue(state.takeNotice(7000, 6000));
        assertTrue(state.noticed());
    }

    @Test
    void noticesOfTheSameRuleRespectTheCooldown() {
        AlarmState state = new AlarmState();
        assertTrue(state.takeNotice(1000, 6000));
        assertFalse(state.takeNotice(2000, 6000));
        assertTrue(state.takeNotice(7000, 6000));
    }

    @Test
    void nearTheLimitIsFlaggedBeforeTheAlarm() {
        assertTrue(BELOW_TWENTY.near(22, HYSTERESIS));
        assertFalse(BELOW_TWENTY.near(30, HYSTERESIS));
        assertFalse(BELOW_TWENTY.near(19, HYSTERESIS));
    }

    @Test
    void aHoldDelaysTheAlarmUntilTheBreachLasts() {
        AlarmRule held = new AlarmRule(4, 7, Kind.VALUE, Comparison.BELOW, 20, Severity.WARNING, "held", 2, Period.FIVE_MINUTES);
        AlarmState state = new AlarmState();
        assertEquals(Transition.NONE, state.update(held, 19, HYSTERESIS));
        assertEquals(Transition.NONE, state.update(held, 19, HYSTERESIS));
        assertEquals(Transition.RAISED, state.update(held, 19, HYSTERESIS));
    }

    @Test
    void aRecoveryDuringTheHoldStartsTheCountAgain() {
        AlarmRule held = new AlarmRule(4, 7, Kind.VALUE, Comparison.BELOW, 20, Severity.WARNING, "held", 1, Period.FIVE_MINUTES);
        AlarmState state = new AlarmState();
        state.update(held, 19, HYSTERESIS);
        state.update(held, 25, HYSTERESIS);
        assertEquals(Transition.NONE, state.update(held, 19, HYSTERESIS));
        assertEquals(Transition.RAISED, state.update(held, 19, HYSTERESIS));
    }

    @Test
    void aMissingReadingRaisesOnlyAfterTheGraceAndClearsWhenItReturns() {
        AlarmRule missing = new AlarmRule(5, 7, Kind.VALUE, Comparison.MISSING, 0, Severity.CRITICAL, "gone");
        AlarmState state = new AlarmState();
        for (int second = 0; second < missing.requiredSeconds(); second++) {
            assertEquals(Transition.NONE, state.update(missing, Double.NaN, HYSTERESIS));
        }
        assertEquals(Transition.RAISED, state.update(missing, Double.NaN, HYSTERESIS));
        assertEquals(Transition.CLEARED, state.update(missing, 3, HYSTERESIS));
    }

    @Test
    void anAcknowledgedAlarmStaysActiveButStopsSoundingUntilItClears() {
        AlarmState state = new AlarmState();
        state.update(BELOW_TWENTY, 19, HYSTERESIS);
        assertTrue(state.acknowledge());
        assertTrue(state.active());
        assertFalse(state.sounding());
        assertFalse(state.acknowledge());
        state.update(BELOW_TWENTY, 25, HYSTERESIS);
        state.update(BELOW_TWENTY, 19, HYSTERESIS);
        assertTrue(state.sounding());
    }
}
