package dev.nostalgiacraft.monitor.metric;

public record Sample(double value, double capacity) {
    public static Sample of(double value) {
        return new Sample(value, 0);
    }
}
