package dev.nostalgiacraft.monitor.alarm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Inbox {
    public static final int MAX_NOTES = 50;
    private final Map<UUID, Deque<String>> notes = new HashMap<>();
    private final Map<UUID, Long> lastAdded = new HashMap<>();

    public void add(UUID player, String note, long time) {
        Deque<String> queue = notes.computeIfAbsent(player, owner -> new ArrayDeque<>());
        queue.addLast(note);
        lastAdded.merge(player, time, Math::max);
        while (queue.size() > MAX_NOTES) {
            queue.removeFirst();
        }
    }

    public List<String> take(UUID player) {
        Deque<String> queue = notes.remove(player);
        lastAdded.remove(player);
        return queue == null ? List.of() : List.copyOf(queue);
    }

    public List<String> peek(UUID player) {
        Deque<String> queue = notes.get(player);
        return queue == null ? List.of() : List.copyOf(queue);
    }

    public long lastAdded(UUID player) {
        return lastAdded.getOrDefault(player, 0L);
    }

    public void dropOlderThan(long cutoff) {
        lastAdded.entrySet().removeIf(entry -> entry.getValue() < cutoff && notes.remove(entry.getKey()) != null);
    }

    public Iterable<UUID> players() {
        return notes.keySet();
    }
}
