package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.client.render.PanelPreview;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.network.Payloads;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import dev.nostalgiacraft.monitor.series.DerivedMetric;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public final class PanelScreen extends WorkshopScreen {
    private static final String ITEM_ICON = "item:";
    private static final int PREVIEW_WIDTH = 150;
    private static final int PREVIEW_GAP = 8;
    private static final int CAPTION = 12;
    private static final int GAP = 6;
    private static final List<Component> PERIODS = List.of(Component.literal("5 min"), Component.literal("6 h"), Component.literal("7 d"));

    private final ScreenBlockEntity screen;
    private final List<ClientChannels.Sensor> sensors = new ArrayList<>();
    private final List<PanelConfig.Row> rows;
    private String channelText;
    private String titleText;
    private PanelConfig.Mode mode;
    private PanelConfig.Theme theme;
    private DerivedMetric.Period period;
    private int scale;
    private boolean automatic;
    private int tab;
    private int rowPage;
    private boolean rowEditor;
    private int editingRow;

    public PanelScreen(ScreenBlockEntity screen) {
        super(text("panel"), text("panel.help"));
        this.screen = screen;
        ClientChannels.View view = ClientChannels.get(screen.channel());
        if (view != null) {
            sensors.addAll(view.sensors());
        }
        PanelConfig config = screen.config();
        mode = config.mode();
        theme = config.theme();
        period = config.period();
        scale = config.scale();
        titleText = config.title();
        automatic = config.rows().isEmpty();
        rows = GuiDrafts.manualRows(config.rows(), sensors.stream().map(ClientChannels.Sensor::id).toList());
        channelText = screen.channel() == null ? ChannelKey.DEFAULT_NAME : screen.channel().name();
    }

    @Override
    protected void init() {
        super.init();
        tabs(new String[]{"tab.appearance", "tab.rows"}, tab, selected -> {
            tab = selected;
            rebuildWidgets();
        });
        if (tab == 0) {
            appearance();
        } else if (rowEditor) {
            editRow();
        } else {
            rowList();
        }
        footer(this::confirm);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int width = previewWidth();
        int left = layout.x() + layout.contentWidth() - width;
        graphics.drawString(font, text("preview", screen.width(), screen.height()), left, layout.y(), MUTED, false);
        PanelPreview.draw(graphics, left, layout.y() + CAPTION, width, layout.contentHeight() - CAPTION, buildConfig(), screen.width(),
                screen.height(), screen.channel());
    }

    private int previewWidth() {
        return Math.min(PREVIEW_WIDTH, layout.contentWidth() * 2 / 5);
    }

    private int controlsWidth() {
        return layout.contentWidth() - previewWidth() - PREVIEW_GAP;
    }

    private void appearance() {
        int x = layout.x();
        int y = layout.y();
        int w = controlsWidth();
        int half = (w - GAP) / 2;
        field(text("channel"), x, y, half, channelText, ChannelKey.MAX_NAME_LENGTH, value -> channelText = value)
                .setTooltip(Tooltip.create(text("channel.help")));
        field(text("label.title"), x + half + GAP, y, w - half - GAP, titleText, PanelConfig.MAX_TITLE_LENGTH, value -> titleText = value);
        choice(text("label.mode"), option("mode", mode), x, y + 36, w,
                () -> chooseEnum("mode", PanelConfig.Mode.values(), value -> mode = value));
        choice(text("label.theme"), option("theme", theme), x, y + 68, half,
                () -> chooseEnum("theme", PanelConfig.Theme.values(), value -> theme = value));
        segments(text("label.scale"), IntStream.rangeClosed(1, PanelLayout.MAX_SCALE).<Component>mapToObj(value -> text("scale.times", value)).toList(),
                scale - 1, x + half + GAP, y + 68, w - half - GAP, selected -> scale = selected + 1);
        segments(text("label.period"), PERIODS, period.ordinal(), x, y + 100, w, selected -> period = DerivedMetric.Period.values()[selected]);
    }

    private void chooseAutomatic() {
        choose(text("rows.choose"), List.of(new ChoiceScreen.Entry<>(true, text("rows.auto")), new ChoiceScreen.Entry<>(false, text("rows.manual"))),
                selected -> automatic = selected);
    }

    private void rowList() {
        int x = layout.x();
        int y = layout.y();
        int w = controlsWidth();
        button(text(automatic ? "rows.auto" : "rows.manual").append("  >"), x, y, w, false, true, this::chooseAutomatic)
                .setTooltip(Tooltip.create(text("rows.choose.help")));
        if (automatic) {
            note(text("rows.auto.help"), x, y + 38, w);
            paragraph(text("rows.auto.detail"), x, y + 54, w);
            button(text("rows.customize"), x, y + 80, w, () -> {
                automatic = false;
                rebuildWidgets();
            });
            return;
        }
        int capacity = layout.rowCapacity();
        int pages = Math.max(1, (rows.size() + capacity - 1) / capacity);
        rowPage = Math.clamp(rowPage, 0, pages - 1);
        button(text("row.add"), x, y + 24, 112, this::addRow).active = rows.size() < PanelConfig.MAX_ROWS;
        label(text("entries", rows.size(), PanelConfig.MAX_ROWS), x + 120, y + 30, w - 120);
        for (int index = rowPage * capacity; index < Math.min(rows.size(), (rowPage + 1) * capacity); index++) {
            int selected = index;
            Component row = text("row.number", index + 1).append(": ").append(sensorName(rows.get(index).sensorId()));
            button(row, x, y + 50 + (index % capacity) * 24, w, false, true, () -> edit(selected)).setTooltip(Tooltip.create(row));
        }
        if (rows.isEmpty()) {
            note(text("rows.empty"), x, y + 56, w);
        }
        int navY = layout.y() + layout.contentHeight() - 20;
        button(text("previous"), x, navY, 74, () -> turnRowPage(-1)).active = rowPage > 0;
        button(text("next"), x + w - 74, navY, 74, () -> turnRowPage(1)).active = rowPage + 1 < pages;
        label(text("page", rowPage + 1, pages), x + 82, navY + 6, w - 164);
    }

    private void addRow() {
        rows.add(PanelConfig.Row.of(sensors.isEmpty() ? 0 : sensors.getFirst().id()));
        edit(rows.size() - 1);
    }

    private void edit(int row) {
        editingRow = row;
        rowEditor = true;
        rebuildWidgets();
    }

    private void turnRowPage(int step) {
        rowPage += step;
        rebuildWidgets();
    }

    private void editRow() {
        int x = layout.x();
        int y = layout.y();
        int w = controlsWidth();
        int half = (w - GAP) / 2;
        PanelConfig.Row row = rows.get(editingRow);
        choice(text("row.sensor", editingRow + 1), sensorName(row.sensorId()), x, y, w, this::chooseSensor);
        choice(text("label.derived"), option("derived", row.derived()), x, y + 36, half,
                () -> chooseEnum("derived", DerivedMetric.Kind.values(), value -> change(rows.get(editingRow).withDerived(value))));
        choice(text("label.chart"), option("chart", row.chart()), x + half + GAP, y + 36, w - half - GAP,
                () -> chooseEnum("chart", PanelConfig.Chart.values(), value -> change(rows.get(editingRow).withChart(value))));
        iconControls(row, x, y + 72, w);
        rowActions(x, y + 108, w);
    }

    private void iconControls(PanelConfig.Row row, int x, int y, int w) {
        int third = (w - 2 * GAP) / 3;
        ItemStack held = minecraft.player.getMainHandItem();
        label(text("icon.current", iconName(row.icon())), x, y, w);
        button(text("icon.auto"), x, y + CAPTION, third, () -> changeAndRebuild(rows.get(editingRow).withIcon(""))).active = !row.icon().isEmpty();
        Button hand = button(text("icon.hand"), x + third + GAP, y + CAPTION, third,
                () -> changeAndRebuild(rows.get(editingRow).withIcon(ITEM_ICON + BuiltInRegistries.ITEM.getKey(held.getItem()))));
        hand.active = !held.isEmpty();
        hand.setTooltip(Tooltip.create(text("icon.hand.help")));
        button(text("icon.pick"), x + 2 * (third + GAP), y + CAPTION, w - 2 * (third + GAP),
                () -> minecraft.setScreen(new IconPickerScreen(this, icon -> change(rows.get(editingRow).withIcon(icon)))));
    }

    private void rowActions(int x, int y, int w) {
        int quarter = (w - 3 * GAP) / 4;
        button(text("row.up"), x, y, quarter, () -> move(-1)).active = editingRow > 0;
        button(text("row.down"), x + quarter + GAP, y, quarter, () -> move(1)).active = editingRow + 1 < rows.size();
        button(text("remove"), x + 2 * (quarter + GAP), y, quarter, () -> {
            rows.remove(editingRow);
            closeRowEditor();
        });
        button(text("back.rows"), x + 3 * (quarter + GAP), y, w - 3 * (quarter + GAP), this::closeRowEditor);
    }

    private void chooseSensor() {
        List<ChoiceScreen.Entry<Integer>> entries = new ArrayList<>();
        entries.add(new ChoiceScreen.Entry<>(0, text("row.empty")));
        sensors.forEach(sensor -> entries.add(new ChoiceScreen.Entry<>(sensor.id(), Component.literal(sensor.name()))));
        choose(text("label.sensor"), entries, value -> change(rows.get(editingRow).withSensor(value)));
    }

    private void change(PanelConfig.Row row) {
        rows.set(editingRow, row);
    }

    private void changeAndRebuild(PanelConfig.Row row) {
        change(row);
        rebuildWidgets();
    }

    private void move(int step) {
        Collections.swap(rows, editingRow, editingRow + step);
        editingRow += step;
        rebuildWidgets();
    }

    private void closeRowEditor() {
        rowEditor = false;
        rebuildWidgets();
    }

    private static Component iconName(String icon) {
        if (icon.isEmpty()) {
            return text("icon.auto");
        }
        ResourceLocation item = icon.startsWith(ITEM_ICON) ? ResourceLocation.tryParse(icon.substring(ITEM_ICON.length())) : null;
        return item == null ? Component.literal(icon) : BuiltInRegistries.ITEM.get(item).getDescription();
    }

    private Component sensorName(int id) {
        return sensors.stream().filter(sensor -> sensor.id() == id).findFirst().<Component>map(sensor -> Component.literal(sensor.name()))
                .orElse(id == 0 ? text("row.empty") : text("sensor.unavailable", id));
    }

    PanelConfig buildConfig() {
        return new PanelConfig(mode, theme, period, scale, automatic ? List.of() : rows, titleText);
    }

    private void confirm() {
        if (!automatic && rows.isEmpty()) {
            tab = 1;
            rowEditor = false;
            status = text("error.rows").getString();
            rebuildWidgets();
            return;
        }
        PacketDistributor.sendToServer(new Payloads.ConfigurePanel(screen.getBlockPos(), channelText, buildConfig().toTag()));
        onClose();
    }
}
