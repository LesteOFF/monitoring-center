package dev.nostalgiacraft.monitor.alarm;

public final class AlarmState {
    public enum Transition {
        NONE, RAISED, CLEARED
    }

    private boolean active;
    private boolean acknowledged;
    private boolean noticed;
    private int breachedSeconds;
    private long lastNoticeTick = Long.MIN_VALUE;

    public Transition update(AlarmRule rule, double value, double hysteresis) {
        return active ? clearing(rule, value, hysteresis) : raising(rule, value);
    }

    private Transition clearing(AlarmRule rule, double value, double hysteresis) {
        if (!rule.recovered(value, hysteresis)) {
            return Transition.NONE;
        }
        active = false;
        acknowledged = false;
        breachedSeconds = 0;
        return Transition.CLEARED;
    }

    private Transition raising(AlarmRule rule, double value) {
        if (!rule.breached(value)) {
            breachedSeconds = 0;
            return Transition.NONE;
        }
        if (breachedSeconds++ < rule.requiredSeconds()) {
            return Transition.NONE;
        }
        active = true;
        return Transition.RAISED;
    }

    public boolean acknowledge() {
        boolean silenced = active && !acknowledged;
        acknowledged = active;
        return silenced;
    }

    public boolean acknowledged() {
        return acknowledged;
    }

    public boolean sounding() {
        return active && !acknowledged;
    }

    public boolean takeNotice(long tick, long cooldownTicks) {
        noticed = lastNoticeTick == Long.MIN_VALUE || tick - lastNoticeTick >= cooldownTicks;
        if (noticed) {
            lastNoticeTick = tick;
        }
        return noticed;
    }

    public boolean takeClearNotice() {
        boolean announce = noticed;
        noticed = false;
        return announce;
    }

    public boolean active() {
        return active;
    }

    public boolean noticed() {
        return noticed;
    }

    public void restore(boolean wasActive) {
        restore(wasActive, false);
    }

    public void restore(boolean wasActive, boolean wasAcknowledged) {
        active = wasActive;
        noticed = wasActive;
        acknowledged = wasActive && wasAcknowledged;
    }
}
