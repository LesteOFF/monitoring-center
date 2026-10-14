package dev.nostalgiacraft.monitor.alarm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void keepsOnlyTheNewestFiftyNotes() {
        Inbox inbox = new Inbox();
        for (int note = 0; note < 60; note++) {
            inbox.add(PLAYER, "note " + note, 0);
        }
        List<String> notes = inbox.take(PLAYER);
        assertEquals(Inbox.MAX_NOTES, notes.size());
        assertEquals("note 10", notes.getFirst());
        assertEquals("note 59", notes.getLast());
    }

    @Test
    void takingTheNotesEmptiesTheInbox() {
        Inbox inbox = new Inbox();
        inbox.add(PLAYER, "one", 0);
        inbox.take(PLAYER);
        assertTrue(inbox.take(PLAYER).isEmpty());
    }

    @Test
    void notesOfOtherPlayersAreUntouched() {
        Inbox inbox = new Inbox();
        inbox.add(PLAYER, "mine", 0);
        assertTrue(inbox.take(UUID.randomUUID()).isEmpty());
        assertEquals(List.of("mine"), inbox.peek(PLAYER));
    }

    @Test
    void notesOlderThanTheCutoffAreDropped() {
        Inbox inbox = new Inbox();
        UUID recent = UUID.randomUUID();
        inbox.add(PLAYER, "old", 100);
        inbox.add(recent, "new", 300);
        inbox.dropOlderThan(200);
        assertTrue(inbox.peek(PLAYER).isEmpty());
        assertEquals(List.of("new"), inbox.peek(recent));
    }
}
