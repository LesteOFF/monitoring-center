package dev.nostalgiacraft.monitor.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RateLimit {
    private record Window(long start, int used) {
    }

    private final int windowTicks;
    private final int budget;
    private final Map<UUID, Window> windows = new HashMap<>();

    RateLimit(int windowTicks, int budget) {
        this.windowTicks = windowTicks;
        this.budget = budget;
    }

    boolean allow(UUID sender, long tick) {
        Window window = windows.get(sender);
        boolean expired = window == null || tick < window.start() || tick - window.start() >= windowTicks;
        Window current = expired ? new Window(tick, 1) : new Window(window.start(), window.used() + 1);
        windows.put(sender, current);
        return current.used() <= budget;
    }

    void forget(UUID sender) {
        windows.remove(sender);
    }
}
