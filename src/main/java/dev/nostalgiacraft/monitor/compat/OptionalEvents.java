package dev.nostalgiacraft.monitor.compat;

import dev.nostalgiacraft.monitor.MonitoringCenter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class OptionalEvents {
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();

    private OptionalEvents() {
    }

    public static void guard(String integration, Runnable action) {
        if (DISABLED.contains(integration)) {
            return;
        }
        try {
            action.run();
        } catch (LinkageError | RuntimeException failure) {
            disable(integration, "registering it", failure);
        }
    }

    public static <T> T guarded(String integration, T fallback, Supplier<T> query) {
        if (DISABLED.contains(integration)) {
            return fallback;
        }
        try {
            return query.get();
        } catch (LinkageError failure) {
            disable(integration, "querying it", failure);
            return fallback;
        } catch (RuntimeException unreadable) {
            return fallback;
        }
    }

    private static void disable(String integration, String step, Throwable failure) {
        if (DISABLED.add(integration)) {
            MonitoringCenter.LOGGER.warn("{} integration disabled: failed while {} with the installed version", integration, step, failure);
        }
    }
}
