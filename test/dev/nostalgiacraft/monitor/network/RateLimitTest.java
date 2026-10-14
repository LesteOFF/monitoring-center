package dev.nostalgiacraft.monitor.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitTest {
    private static final UUID SENDER = UUID.randomUUID();

    @Test
    void packetsBeyondTheBudgetAreRefusedUntilTheWindowEnds() {
        RateLimit limit = new RateLimit(20, 3);
        for (int packet = 0; packet < 3; packet++) {
            assertTrue(limit.allow(SENDER, 100));
        }
        assertFalse(limit.allow(SENDER, 119));
        assertTrue(limit.allow(SENDER, 120));
    }

    @Test
    void eachSenderHasItsOwnBudget() {
        RateLimit limit = new RateLimit(20, 1);
        assertTrue(limit.allow(SENDER, 0));
        assertTrue(limit.allow(UUID.randomUUID(), 0));
        assertFalse(limit.allow(SENDER, 1));
    }
}
