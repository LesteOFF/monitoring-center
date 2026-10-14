package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

abstract class WorkshopScreen extends Screen {
    static final String PREFIX = "screen.monitoringcenter.";
    static final int INK = 0xFFE6E6E6;
    static final int MUTED = 0xFF9A9A9A;
    static final int ACCENT = 0xFFFFB000;
    private static final int SEGMENT_GAP = 2;
    private static final int NARROWEST_SEGMENT = 40;
    private static final int ERROR = 0xFFFF6B5E;
    private static final int ON_PRIMARY = 0xFF232323;
    private static final int DISABLED = 0xFF6E6E6E;
    private static final ResourceLocation WINDOW = sprite("window");
    private static final ResourceLocation CONTENT = sprite("content");
    private static final ResourceLocation FIELD = sprite("field");
    private static final ResourceLocation FIELD_FOCUSED = sprite("field_focused");
    private static final ResourceLocation BUTTON = sprite("button");
    private static final ResourceLocation BUTTON_HOVER = sprite("button_hover");
    private static final ResourceLocation BUTTON_PRIMARY = sprite("button_primary");
    private static final ResourceLocation BUTTON_PRIMARY_HOVER = sprite("button_primary_hover");
    private static final ResourceLocation BUTTON_PRIMARY_DISABLED = sprite("button_primary_disabled");
    protected WorkshopLayout layout;
    protected String status = "";
    private final Component subtitle;
    private final List<Label> labels = new ArrayList<>();
    private final List<EditBox> fields = new ArrayList<>();
    private final List<Paragraph> paragraphs = new ArrayList<>();

    WorkshopScreen(Component title, Component subtitle) {
        super(title);
        this.subtitle = subtitle;
    }

    @Override
    protected void init() {
        layout = WorkshopLayout.fit(width, height);
        labels.clear();
        fields.clear();
        paragraphs.clear();
    }

    private static ResourceLocation sprite(String name) {
        return MonitoringCenter.id("workshop/" + name);
    }

    protected static MutableComponent text(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    protected static MutableComponent option(String group, Enum<?> value) {
        return text(group + "." + value.name().toLowerCase(Locale.ROOT));
    }

    protected void label(Component text, int x, int y, int maxWidth) {
        labels.add(new Label(text, x, y, maxWidth, MUTED));
    }

    protected void note(Component text, int x, int y, int maxWidth) {
        labels.add(new Label(text, x, y, maxWidth, INK));
    }

    protected void paragraph(Component text, int x, int y, int maxWidth) {
        paragraphs.add(new Paragraph(font.split(text, maxWidth), x, y));
    }

    protected EditBox field(Component label, int x, int y, int width, String value, int maxLength, Consumer<String> changed) {
        label(label, x, y, width);
        EditBox input = addRenderableWidget(new EditBox(font, x + 5, y + 16, width - 10, 12, label));
        input.setBordered(false);
        input.setTextColor(INK);
        input.setTextColorUneditable(MUTED);
        input.setMaxLength(maxLength);
        input.setValue(value);
        input.setResponder(changed);
        fields.add(input);
        return input;
    }

    protected Button button(Component title, int x, int y, int width, Runnable clicked) {
        return button(title, x, y, width, false, false, clicked);
    }

    protected Button button(Component title, int x, int y, int width, boolean primary, boolean alignLeft, Runnable clicked) {
        return addRenderableWidget(new WorkshopButton(x, y, width, title, primary, alignLeft, clicked));
    }

    protected Button choice(Component label, Component selected, int x, int y, int width, Runnable clicked) {
        label(label, x, y, width);
        Button button = button(selected.copy().append("  >"), x, y + 12, width, false, true, clicked);
        button.setTooltip(Tooltip.create(label.copy().append(": ").append(selected)));
        return button;
    }

    protected void segments(Component title, List<Component> options, int selected, int x, int y, int width, IntConsumer changed) {
        int each = (width - SEGMENT_GAP * (options.size() - 1)) / options.size();
        if (each < NARROWEST_SEGMENT) {
            choice(title, options.get(selected), x, y, width, () -> choose(title, IntStream.range(0, options.size())
                    .mapToObj(index -> new ChoiceScreen.Entry<>(index, options.get(index))).toList(), changed::accept));
            return;
        }
        label(title, x, y, width);
        for (int index = 0; index < options.size(); index++) {
            int option = index;
            int left = x + index * (each + SEGMENT_GAP);
            int segmentWidth = index == options.size() - 1 ? x + width - left : each;
            button(options.get(index), left, y + 12, segmentWidth, index == selected, false, () -> {
                changed.accept(option);
                rebuildWidgets();
            }).setTooltip(Tooltip.create(options.get(index)));
        }
    }

    protected void tabs(String[] names, int selected, Consumer<Integer> change) {
        int gap = 4;
        int each = (layout.contentWidth() - gap * (names.length - 1)) / names.length;
        for (int index = 0; index < names.length; index++) {
            int tab = index;
            button(text(names[index]), layout.x() + index * (each + gap), layout.top() + 36, each,
                    selected == index, false, () -> change.accept(tab));
        }
    }

    protected void footer(Runnable save) {
        int gap = 6;
        int saveWidth = Math.min(174, layout.contentWidth() * 2 / 3);
        button(text("cancel"), layout.x(), layout.footerY(), layout.contentWidth() - saveWidth - gap, this::onClose);
        button(text("save"), layout.x() + layout.contentWidth() - saveWidth, layout.footerY(), saveWidth, true, false, save);
    }

    protected <T> void choose(Component title, List<ChoiceScreen.Entry<T>> entries, Consumer<T> selected) {
        minecraft.setScreen(new ChoiceScreen<>(this, title, entries, selected));
    }

    protected <E extends Enum<E>> void chooseEnum(String group, E[] values, Consumer<E> selected) {
        List<ChoiceScreen.Entry<E>> entries = new ArrayList<>();
        for (E value : values) {
            Component name = option(group, value);
            Component description = group.equals("derived") ? name.copy().append("\n").append(text("derived.help." + value.name().toLowerCase(Locale.ROOT))) : name;
            entries.add(new ChoiceScreen.Entry<>(value, name, description));
        }
        choose(text("label." + group), entries, selected);
    }

    protected void pages(int page, int count, Consumer<Integer> changed) {
        int y = layout.y() + layout.contentHeight() - 20;
        Button previous = button(text("previous"), layout.x(), y, 74, () -> changed.accept(page - 1));
        previous.active = page > 0;
        Button next = button(text("next"), layout.x() + layout.contentWidth() - 74, y, 74, () -> changed.accept(page + 1));
        next.active = page + 1 < layout.pageCount(count);
        label(text("page", page + 1, layout.pageCount(count)), layout.x() + 82, y + 6, layout.contentWidth() - 164);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(WINDOW, layout.left(), layout.top(), layout.width(), layout.height());
        graphics.blitSprite(CONTENT, layout.x() - 3, layout.y() - 3, layout.contentWidth() + 6, layout.footerY() - layout.y() - 3);
        graphics.drawString(font, title, layout.x(), layout.top() + 8, ACCENT, false);
        String caption = status.isEmpty() ? subtitle.getString() : status;
        graphics.drawString(font, font.plainSubstrByWidth(caption, layout.contentWidth()), layout.x(), layout.top() + 21,
                status.isEmpty() ? MUTED : ERROR, false);
        for (Label label : labels) graphics.drawString(font, font.plainSubstrByWidth(label.text().getString(), label.width()), label.x(), label.y(), label.color(), false);
        for (Paragraph paragraph : paragraphs) {
            for (int index = 0; index < paragraph.lines().size(); index++)
                graphics.drawString(font, paragraph.lines().get(index), paragraph.x(), paragraph.y() + index * 10, MUTED, false);
        }
        for (EditBox field : fields) {
            int x = field.getX() - 5;
            int y = field.getY() - 4;
            graphics.blitSprite(field.isFocused() ? FIELD_FOCUSED : FIELD, x, y, field.getWidth() + 10, 20);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record Label(Component text, int x, int y, int width, int color) {}
    private record Paragraph(List<FormattedCharSequence> lines, int x, int y) {}

    private static final class WorkshopButton extends Button {
        private final boolean primary;
        private final boolean alignLeft;

        WorkshopButton(int x, int y, int width, Component message, boolean primary, boolean alignLeft, Runnable clicked) {
            super(x, y, width, 20, message, button -> clicked.run(), DEFAULT_NARRATION);
            this.primary = primary;
            this.alignLeft = alignLeft;
        }

        private ResourceLocation background() {
            boolean lit = active && isHoveredOrFocused();
            if (primary) {
                return !active ? BUTTON_PRIMARY_DISABLED : lit ? BUTTON_PRIMARY_HOVER : BUTTON_PRIMARY;
            }
            return lit ? BUTTON_HOVER : BUTTON;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blitSprite(background(), getX(), getY(), getWidth(), getHeight());
            int color = !active ? DISABLED : primary ? ON_PRIMARY : INK;
            if (alignLeft) {
                String clipped = Minecraft.getInstance().font.plainSubstrByWidth(getMessage().getString(), getWidth() - 12);
                graphics.drawString(Minecraft.getInstance().font, clipped, getX() + 6, getY() + 6, color, false);
            } else {
                var font = Minecraft.getInstance().font;
                String clipped = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 8);
                graphics.drawString(font, clipped, getX() + (getWidth() - font.width(clipped)) / 2, getY() + 6, color, false);
            }
        }
    }
}
