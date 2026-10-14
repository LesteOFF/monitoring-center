package dev.nostalgiacraft.monitor.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerStateTest {
    private static final PowerState.Spec SPEC = new PowerState.Spec(40, 4000);

    private static PowerState charged() {
        PowerState state = new PowerState();
        state.receive(SPEC, SPEC.capacity(), false);
        return state;
    }

    @Test
    void emptyBatteryIsOff() {
        assertFalse(new PowerState().tick(SPEC, false));
    }

    @Test
    void fullBatterySurvivesAShortOutage() {
        PowerState state = charged();
        for (int tick = 0; tick < 100; tick++) {
            assertTrue(state.tick(SPEC, false), "tick " + tick);
        }
    }

    @Test
    void batteryTurnsOffInALongOutage() {
        PowerState state = charged();
        for (int tick = 0; tick < 100; tick++) {
            state.tick(SPEC, false);
        }
        assertFalse(state.tick(SPEC, false));
        assertFalse(state.powered());
    }

    @Test
    void receivingNeverExceedsTheCapacity() {
        PowerState state = new PowerState();
        assertEquals(4000, state.receive(SPEC, 5000, false));
        assertEquals(0, state.receive(SPEC, 10, false));
        assertEquals(4000, state.stored());
    }

    @Test
    void simulatedReceiveStoresNothing() {
        PowerState state = new PowerState();
        assertEquals(100, state.receive(SPEC, 100, true));
        assertEquals(0, state.stored());
    }

    @Test
    void rotationKeepsTheBatteryFullWithoutSpendingEnergy() {
        PowerState state = new PowerState();
        assertTrue(state.tick(SPEC, true));
        assertEquals(SPEC.capacity(), state.stored());
    }

    @Test
    void powerComesBackOnItsOwn() {
        PowerState state = new PowerState();
        state.tick(SPEC, false);
        state.receive(SPEC, SPEC.restartReserve(), false);
        assertTrue(state.tick(SPEC, false));
    }

    @Test
    void anUnderfedCentralWaitsForAReserveInsteadOfFlickering() {
        PowerState state = new PowerState();
        int switchedOn = 0;
        boolean wasOn = false;
        for (int tick = 0; tick < 400; tick++) {
            state.receive(SPEC, 10, false);
            boolean on = state.tick(SPEC, false);
            if (on && !wasOn) {
                switchedOn++;
            }
            wasOn = on;
        }
        assertTrue(switchedOn <= 4, "switched on " + switchedOn + " times");
    }
}
