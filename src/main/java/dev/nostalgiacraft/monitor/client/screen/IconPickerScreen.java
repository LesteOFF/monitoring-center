package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.client.render.PanelIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class IconPickerScreen extends WorkshopScreen {
    private static final String ITEM_PREFIX = "item:";
    private static final int CELL = 18;
    private static final int GRID_TOP = 14;
    private static final int HOVER = 0x50FFFFFF;
    private static List<Icon> catalog;

    private record Icon(String id, ItemStack stack, Component name, String search) {
        boolean symbol() {
            return stack.isEmpty();
        }
    }

    private final WorkshopScreen parent;
    private final Consumer<String> selected;
    private List<Icon> found = List.of();
    private String filter = "";
    private int firstRow;

    IconPickerScreen(WorkshopScreen parent, Consumer<String> selected) {
        super(text("icon.pick"), text("icon.pick.help"));
        this.parent = parent;
        this.selected = selected;
    }

    private static List<Icon> catalog() {
        if (catalog == null) {
            List<Icon> icons = new ArrayList<>();
            for (String symbol : PanelIcons.GLYPHS) {
                icons.add(new Icon(symbol, ItemStack.EMPTY, Component.literal(symbol), symbol));
            }
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) {
                    Component name = item.getDescription();
                    String id = BuiltInRegistries.ITEM.getKey(item).toString();
                    icons.add(new Icon(ITEM_PREFIX + id, new ItemStack(item), name, (name.getString() + " " + id).toLowerCase(Locale.ROOT)));
                }
            }
            catalog = List.copyOf(icons);
        }
        return catalog;
    }

    @Override
    protected void init() {
        super.init();
        setInitialFocus(field(text("search"), layout.x(), layout.top() + 36, layout.contentWidth(), filter, 64, this::search));
        button(text("back"), layout.x(), layout.footerY(), layout.contentWidth(), this::onClose);
        search(filter);
    }

    private void search(String text) {
        filter = text;
        String wanted = text.toLowerCase(Locale.ROOT).strip();
        found = catalog().stream().filter(icon -> icon.search().contains(wanted)).toList();
        scrollTo(0);
    }

    private int columns() {
        return layout.contentWidth() / CELL;
    }

    private int visibleRows() {
        return Math.max(1, (layout.contentHeight() - GRID_TOP - 12) / CELL);
    }

    private int gridTop() {
        return layout.y() + GRID_TOP;
    }

    private void scrollTo(int row) {
        int rows = (found.size() + columns() - 1) / columns();
        firstRow = Math.clamp(row, 0, Math.max(0, rows - visibleRows()));
    }

    @Nullable
    private Icon at(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - layout.x()) / CELL);
        int row = (int) Math.floor((mouseY - gridTop()) / CELL);
        int index = (firstRow + row) * columns() + column;
        boolean inside = column >= 0 && column < columns() && row >= 0 && row < visibleRows();
        return inside && index < found.size() ? found.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTo(firstRow - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Icon icon = at(mouseX, mouseY);
        if (icon == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        selected.accept(icon.id());
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int columns = columns();
        Icon hovered = at(mouseX, mouseY);
        for (int slot = 0; slot < visibleRows() * columns; slot++) {
            int index = firstRow * columns + slot;
            if (index >= found.size()) {
                break;
            }
            Icon icon = found.get(index);
            int x = layout.x() + slot % columns * CELL;
            int y = gridTop() + slot / columns * CELL;
            if (icon == hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, HOVER);
            }
            if (icon.symbol()) {
                PanelIcons.blitGlyph(graphics, icon.id(), x + 1, y + 1, ACCENT);
            } else {
                graphics.renderItem(icon.stack(), x + 1, y + 1);
            }
        }
        graphics.drawString(font, text("icon.count", found.size()), layout.x(), layout.y() + layout.contentHeight() - 8, MUTED, false);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.name(), mouseX, mouseY);
        }
    }
}
