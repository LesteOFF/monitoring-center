package dev.nostalgiacraft.monitor.client.screen;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopLayoutTest {
    @Test
    void editorsAndActionsFitAtEverySupportedGuiSize() {
        for (int width : new int[]{320, 360, 480, 854, 1920}) {
            for (int height : new int[]{240, 270, 300, 480, 1080}) {
                WorkshopLayout panel = WorkshopLayout.fit(width, height);
                String context = width + "x" + height;
                assertTrue(panel.left() >= 0 && panel.top() >= 0, context);
                assertTrue(panel.left() + panel.width() <= width, context);
                assertTrue(panel.top() + panel.height() <= height, context);
                // The longest editor has three labeled fields and its Back-to-rows action at +104.
                assertTrue(panel.y() + 124 <= panel.footerY() - 4, context);
                assertTrue(panel.footerY() + 20 < panel.top() + panel.height(), context);
                assertTrue(panel.contentWidth() >= 280, context);
            }
        }
    }

    @Test
    void fullCentralAndPanelListsStayAbovePaginationAndExposeEveryEntry() {
        for (int width : new int[]{320, 480, 1920}) for (int height : new int[]{240, 300, 1080}) {
            WorkshopLayout panel = WorkshopLayout.fit(width, height);
            assertPages(panel, panel.listCapacity(), 26, 8);
            assertPages(panel, panel.rowCapacity(), 50, 16);
            assertPages(panel, panel.choiceCapacity(), 14, 200);
        }
    }

    private static void assertPages(WorkshopLayout panel, int capacity, int firstY, int entries) {
        int navigationY = panel.y() + panel.contentHeight() - 20;
        assertTrue(panel.y() + firstY + (capacity - 1) * 24 + 20 <= navigationY - 2,
                "Last entry must not overlap page buttons: " + panel);
        Set<Integer> seen = new HashSet<>();
        int pages = (entries + capacity - 1) / capacity;
        for (int page = 0; page < pages; page++) {
            for (int index = page * capacity; index < Math.min(entries, (page + 1) * capacity); index++) assertTrue(seen.add(index));
        }
        assertEquals(entries, seen.size());
    }

    @Test
    void deletingTheLastPageClampsToAnExistingPage() {
        WorkshopLayout panel = WorkshopLayout.fit(320, 240);
        assertEquals(3, panel.listCapacity());
        assertEquals(2, panel.clampPage(2, 8));
        assertEquals(1, panel.clampPage(2, 6));
        assertEquals(0, panel.clampPage(5, 0));
        assertEquals(0, panel.clampPage(-1, 8));
    }
}
