package dev.nostalgiacraft.monitor.panel;

import dev.nostalgiacraft.monitor.panel.PanelLayout.Card;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Cards;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Graph;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Layout;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Rect;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Row;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelLayoutTest {
    private static final int[] ITEM_COUNTS = {0, 3, 200};

    private static void everySize(BiConsumer<String, Size> check) {
        for (int width = 1; width <= PanelLayout.MAX_WIDTH; width++) {
            for (int height = 1; height <= PanelLayout.MAX_HEIGHT; height++) {
                for (int scale = 1; scale <= PanelLayout.MAX_SCALE; scale++) {
                    check.accept(width + "x" + height + " @" + scale, PanelLayout.size(width, height, scale));
                }
            }
        }
    }

    private static List<Rect> present(Rect... rects) {
        return Stream.of(rects).filter(rect -> !rect.empty()).toList();
    }

    private static void assertFits(List<Rect> rects, Rect outer, String context) {
        for (int first = 0; first < rects.size(); first++) {
            assertTrue(rects.get(first).inside(outer), context + ": " + rects.get(first) + " outside " + outer);
            for (int second = first + 1; second < rects.size(); second++) {
                assertFalse(rects.get(first).overlaps(rects.get(second)), context + ": " + rects.get(first) + " x " + rects.get(second));
            }
        }
    }

    @Test
    void listRowsStayInsideThePanelAndNeverOverlap() {
        everySize((context, size) -> {
            for (int items : ITEM_COUNTS) {
                Layout layout = PanelLayout.list(size, items);
                assertFalse(layout.rows().isEmpty(), context);
                List<Rect> bands = Stream.concat(present(layout.header(), layout.footer()).stream(),
                        layout.rows().stream().map(Row::bounds)).toList();
                assertFits(bands, size.bounds(), context);
                for (Row row : layout.rows()) {
                    assertFits(present(row.icon(), row.name(), row.value(), row.chart(), row.trend()), row.bounds(), context);
                }
            }
        });
    }

    @Test
    void aBiggerPanelShowsMoreRowsAndMoreColumns() {
        assertEquals(5, PanelLayout.list(PanelLayout.size(1, 1, 1), 0).rows().size());
        assertTrue(PanelLayout.list(PanelLayout.size(1, 4, 1), 0).rows().size() > PanelLayout.list(PanelLayout.size(1, 2, 1), 0).rows().size());
        Row narrow = PanelLayout.list(PanelLayout.size(1, 1, 1), 0).rows().getFirst();
        Row wide = PanelLayout.list(PanelLayout.size(4, 1, 1), 0).rows().getFirst();
        assertTrue(narrow.name().empty() && narrow.chart().empty() && narrow.trend().empty());
        assertFalse(wide.name().empty() || wide.chart().empty() || wide.trend().empty());
        assertEquals(2, PanelLayout.cards(PanelLayout.size(1, 1, 1), 2).cards().size());
        assertTrue(PanelLayout.cards(PanelLayout.size(4, 4, 1), 0).cards().size() > PanelLayout.cards(PanelLayout.size(2, 2, 1), 0).cards().size());
    }

    @Test
    void textScaleMakesAPanelBehaveLikeASmallerOne() {
        Layout small = PanelLayout.list(PanelLayout.size(2, 2, 1), 0);
        Layout scaled = PanelLayout.list(PanelLayout.size(4, 4, 2), 0);
        assertEquals(small.rows().size(), scaled.rows().size());
        assertEquals(small.rows().getFirst().chart().empty(), scaled.rows().getFirst().chart().empty());
        assertTrue(scaled.rows().size() < PanelLayout.list(PanelLayout.size(4, 4, 1), 0).rows().size());
        assertEquals(PanelLayout.size(1, 1, 1), PanelLayout.size(1, 1, PanelLayout.MAX_SCALE));
        assertEquals(1, PanelLayout.scale(1, 8, 3));
        assertEquals(3, PanelLayout.scale(3, 8, 3));
    }

    @Test
    void aClickFindsTheRowDrawnUnderIt() {
        for (int height = 1; height <= PanelLayout.MAX_HEIGHT; height++) {
            for (int scale = 1; scale <= PanelLayout.MAX_SCALE; scale++) {
                Size size = PanelLayout.size(height, height, scale);
                int applied = PanelLayout.scale(height, height, scale);
                List<Row> rows = PanelLayout.list(size, 0).rows();
                for (int index = 0; index < rows.size(); index++) {
                    Rect bounds = rows.get(index).bounds();
                    double blocksFromTop = (PanelLayout.MARGIN + applied * (bounds.y() + bounds.height() / 2.0)) / PanelLayout.PIXELS_PER_BLOCK;
                    assertEquals(index, PanelLayout.rowAt(size, PanelLayout.pixel(blocksFromTop, applied)), height + " @" + scale);
                }
            }
        }
    }

    @Test
    void theFooterOnlyAppearsWhenItemsOverflowAndSplitsIntoBackAndForward() {
        Size size = PanelLayout.size(2, 1, 1);
        assertTrue(PanelLayout.list(size, 5).footer().empty());
        Layout paged = PanelLayout.list(size, 6);
        assertEquals(4, paged.rows().size());
        assertEquals(2, PanelLayout.pageCount(6, paged.rows().size()));
        assertEquals(1, PanelLayout.pageCount(0, paged.rows().size()));
        Rect footer = paged.footer();
        assertEquals(-1, PanelLayout.footerStep(footer, footer.x() + 1, footer.y() + 1));
        assertEquals(1, PanelLayout.footerStep(footer, footer.right() - 1, footer.y() + 1));
        assertEquals(0, PanelLayout.footerStep(footer, footer.x() + 1, footer.y() - 1));
        assertTrue(PanelLayout.footer(PanelConfig.Mode.GRAPH, size, 99).empty());
        assertFalse(PanelLayout.footer(PanelConfig.Mode.CARDS, size, 99).empty());
    }

    @Test
    void cardsGraphAndHighlightStayInsideThePanel() {
        everySize((context, size) -> {
            for (int items : ITEM_COUNTS) {
                Cards cards = PanelLayout.cards(size, items);
                assertFalse(cards.cards().isEmpty(), context);
                assertFits(Stream.concat(present(cards.footer()).stream(), cards.cards().stream().map(Card::bounds)).toList(),
                        size.bounds(), "cards " + context);
                for (Card card : cards.cards()) {
                    assertFits(present(card.icon(), card.name(), card.value()), card.bounds(), "card " + context);
                }
            }
            for (int series = 0; series <= PanelConfig.GRAPH_SERIES; series++) {
                Graph graph = PanelLayout.graph(size, series);
                assertFalse(graph.plot().empty(), "graph " + context);
                assertTrue(graph.plot().height() >= 3 * PanelLayout.LINE, "graph " + context + " plot is too short");
                assertFits(Stream.concat(graph.legend().stream(), present(graph.plot(), graph.axis(), graph.timeline()).stream()).toList(),
                        size.bounds(), "graph " + context);
            }
            assertEquals(PanelConfig.GRAPH_SERIES, PanelLayout.graph(PanelLayout.size(4, 3, 1), PanelConfig.GRAPH_SERIES).legend().size());
            Card highlight = PanelLayout.highlight(size);
            assertFits(present(highlight.icon(), highlight.name(), highlight.value()), size.bounds(), "highlight " + context);
        });
    }

    @Test
    void gaugesPageLikeCards() {
        PanelLayout.Size size = PanelLayout.size(2, 1, 1);
        assertEquals(PanelLayout.pageSize(PanelConfig.Mode.CARDS, size, 99), PanelLayout.pageSize(PanelConfig.Mode.GAUGE, size, 99));
        assertEquals(PanelLayout.footer(PanelConfig.Mode.CARDS, size, 99), PanelLayout.footer(PanelConfig.Mode.GAUGE, size, 99));
    }
}
