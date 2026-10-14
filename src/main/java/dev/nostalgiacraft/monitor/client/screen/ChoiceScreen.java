package dev.nostalgiacraft.monitor.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Explicit, searchable choices instead of cycling through undocumented values. */
final class ChoiceScreen<T> extends WorkshopScreen {
    record Entry<T>(T value, Component label, Component description) {
        Entry(T value, Component label) { this(value, label, label); }
    }
    private final WorkshopScreen parent;
    private final List<Entry<T>> entries;
    private final Consumer<T> selected;
    private String filter = "";
    private int page;
    private int foundCount;
    private int pageCount = 1;

    ChoiceScreen(WorkshopScreen parent, Component title, List<Entry<T>> entries, Consumer<T> selected) {
        super(title, text("choose.help"));
        this.parent = parent;
        this.entries = List.copyOf(entries);
        this.selected = selected;
    }

    @Override
    protected void init() {
        super.init();
        field(text("search"), layout.x(), layout.top() + 36, layout.contentWidth(), filter, 64, value -> {
            filter = value;
            page = 0;
            refreshChoices();
        });
        refreshChoices();
    }

    private void refreshChoices() {
        // Keep the search field and its keyboard focus while rebuilding only result buttons.
        for (var child : List.copyOf(children())) if (child instanceof net.minecraft.client.gui.components.Button) removeWidget(child);
        List<Entry<T>> found = entries.stream().filter(entry -> entry.label().getString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();
        int capacity = layout.choiceCapacity();
        int pages = Math.max(1, (found.size() + capacity - 1) / capacity);
        foundCount = found.size();
        pageCount = pages;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int index = page * capacity; index < Math.min(found.size(), (page + 1) * capacity); index++) {
            Entry<T> entry = found.get(index);
            button(entry.label(), layout.x(), layout.y() + 14 + (index % capacity) * 24, layout.contentWidth(), false, true, () -> {
                selected.accept(entry.value());
                minecraft.setScreen(parent);
            }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(entry.description()));
        }
        int navY = layout.y() + layout.contentHeight() - 20;
        var previous = button(text("previous"), layout.x(), navY, 80, () -> { page--; refreshChoices(); });
        previous.active = page > 0;
        var next = button(text("next"), layout.x() + layout.contentWidth() - 80, navY, 80, () -> { page++; refreshChoices(); });
        next.active = page + 1 < pages;
        button(text("back"), layout.x(), layout.footerY(), layout.contentWidth(), this::onClose);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (foundCount == 0) {
            graphics.drawString(font, text("choose.empty"), layout.x(), layout.y() + 22, INK, false);
            graphics.drawString(font, text("choose.empty.help"), layout.x(), layout.y() + 38, MUTED, false);
        }
        Component counter = text("page", page + 1, pageCount);
        graphics.drawString(font, counter, (width - font.width(counter)) / 2, layout.y() + layout.contentHeight() - 14, MUTED, false);
    }
}
