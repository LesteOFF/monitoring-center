package dev.nostalgiacraft.monitor.power;

public final class PowerState {
    public record Spec(int costPerTick, int capacity) {
        private static final int RESTART_TICKS = 20;

        public int restartReserve() {
            return (int) Math.min(capacity, (long) costPerTick * RESTART_TICKS);
        }
    }

    private int stored;
    private boolean powered;

    public boolean tick(Spec spec, boolean drivenByRotation) {
        if (drivenByRotation) {
            stored = spec.capacity();
            powered = true;
        } else if (stored >= (powered ? spec.costPerTick() : spec.restartReserve())) {
            stored -= spec.costPerTick();
            powered = true;
        } else {
            powered = false;
        }
        return powered;
    }

    public int receive(Spec spec, int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, spec.capacity() - stored));
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    public int stored() {
        return stored;
    }

    public boolean powered() {
        return powered;
    }

    public void restore(int energy) {
        stored = Math.max(0, energy);
    }
}
