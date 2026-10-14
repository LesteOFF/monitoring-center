package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class GuiDraftsTest {
    @Test
    void acceptsDecimalCommaAndRejectsInvalidAndNonFiniteAlarmLimits() {
        assertEquals(2.5, GuiDrafts.finiteNumber(" 2,5 "));
        assertEquals(-100.0, GuiDrafts.finiteNumber("-100"));
        assertEquals(1000.0, GuiDrafts.finiteNumber("1e3"));
        for (String invalid : List.of("", "abc", "NaN", "Infinity", "-Infinity", "1e999", "1,2,3")) assertNull(GuiDrafts.finiteNumber(invalid), invalid);
    }

    @Test
    void manualDraftKeepsAllSixteenRowsAndCustomDataIncludingOfflineSensors() {
        List<PanelConfig.Row> previous = IntStream.range(0, PanelConfig.MAX_ROWS)
                .mapToObj(index -> new PanelConfig.Row(index + 100, Kind.AVERAGE, "item:minecraft:diamond", PanelConfig.Chart.BAR, 0xFF987654 + index)).toList();
        List<PanelConfig.Row> draft = GuiDrafts.manualRows(previous, List.of(1, 2));
        assertEquals(previous, draft);
        draft.set(15, draft.get(15).withDerived(Kind.MAXIMUM));
        assertEquals(16, draft.size());
        assertEquals(115, draft.get(15).sensorId());
        assertEquals(previous.get(15).icon(), draft.get(15).icon());
        assertEquals(previous.get(15).color(), draft.get(15).color());
        assertEquals(Kind.AVERAGE, previous.get(15).derived(), "Editing must not mutate the received config");
    }

    @Test
    void switchingAnAutomaticPanelToManualStartsWithItsSensorsUpToTheServerLimit() {
        List<Integer> sensorIds = IntStream.rangeClosed(1, 30).boxed().toList();
        List<PanelConfig.Row> draft = GuiDrafts.manualRows(List.of(), sensorIds);
        assertEquals(PanelConfig.MAX_ROWS, draft.size());
        assertEquals(1, draft.getFirst().sensorId());
        assertEquals(16, draft.getLast().sensorId());
        assertTrue(GuiDrafts.manualRows(List.of(), List.of()).isEmpty());
    }
}
