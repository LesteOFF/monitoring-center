package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.panel.PanelConfig;
import java.util.ArrayList;
import java.util.List;

/** Data operations used by the editors without depending on a running game window. */
public final class GuiDrafts {
    private GuiDrafts() {}
    public static Double finiteNumber(String text) {
        try {
            double value = Double.parseDouble(text.strip().replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }
    public static List<PanelConfig.Row> manualRows(List<PanelConfig.Row> previous, List<Integer> sensorIds) {
        if (!previous.isEmpty()) return new ArrayList<>(previous);
        return new ArrayList<>(sensorIds.stream().limit(PanelConfig.MAX_ROWS).map(PanelConfig.Row::of).toList());
    }
}
