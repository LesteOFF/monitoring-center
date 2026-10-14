package dev.nostalgiacraft.monitor.series;

public final class RingBuffer {
    private final float[] samples;
    private int next;
    private int size;

    public RingBuffer(int capacity) {
        samples = new float[capacity];
    }

    public void push(float sample) {
        samples[next] = sample;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return samples.length;
    }

    public float get(int indexFromOldest) {
        return samples[Math.floorMod(next - size + indexFromOldest, samples.length)];
    }

    public float last() {
        return size == 0 ? Float.NaN : get(size - 1);
    }

    public float[] toArray() {
        float[] copy = new float[size];
        for (int index = 0; index < size; index++) {
            copy[index] = get(index);
        }
        return copy;
    }

    public void restore(float[] oldestFirst) {
        next = 0;
        size = 0;
        for (int index = Math.max(0, oldestFirst.length - samples.length); index < oldestFirst.length; index++) {
            push(oldestFirst[index]);
        }
    }
}
