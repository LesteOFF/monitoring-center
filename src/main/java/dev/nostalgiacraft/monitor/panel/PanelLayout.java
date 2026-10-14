package dev.nostalgiacraft.monitor.panel;

import java.util.ArrayList;
import java.util.List;

public final class PanelLayout {
    public static final int MAX_WIDTH = 12;
    public static final int MAX_HEIGHT = 8;
    public static final int MAX_SCALE = 4;
    public static final int PIXELS_PER_BLOCK = 64;
    public static final int MARGIN = 4;
    public static final int LINE = 10;
    public static final int GLYPH = 8;
    public static final int MAX_LINES = content(MAX_HEIGHT) / LINE;
    private static final int GAP = 2;
    private static final int HEADER_LINES = 6;
    private static final int NAME_WIDTH = 100;
    private static final int TREND_WIDTH = 164;
    private static final int CHART_WIDTH = 228;
    private static final int CARD_WIDTH = 56;
    private static final int CARD_VALUE_HEIGHT = 2 * GLYPH + 2;
    private static final int CARD_HEIGHT = LINE + CARD_VALUE_HEIGHT;
    private static final int HIGHLIGHT_ICON_SHARE = 4;
    private static final int AXIS_WIDTH = 30;
    private static final int LEGEND_COLUMN = 110;
    private static final int MIN_PLOT_LINES = 3;
    private static final Rect NONE = new Rect(0, 0, 0, 0);

    public record Rect(int x, int y, int width, int height) {
        public boolean empty() {
            return width <= 0 || height <= 0;
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(int pixelX, int pixelY) {
            return pixelX >= x && pixelX < right() && pixelY >= y && pixelY < bottom();
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public boolean inside(Rect outer) {
            return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
        }
    }

    public record Size(int width, int height) {
        public Rect bounds() {
            return new Rect(0, 0, width, height);
        }

        int lines() {
            return height / LINE;
        }

        int headerLines() {
            return lines() >= HEADER_LINES ? 1 : 0;
        }
    }

    public record Row(Rect bounds, Rect icon, Rect name, Rect value, Rect chart, Rect trend) {
    }

    public record Layout(Rect header, List<Row> rows, Rect footer) {
    }

    public record Card(Rect bounds, Rect icon, Rect name, Rect value) {
    }

    public record Cards(List<Card> cards, Rect footer) {
    }

    public record Graph(Rect plot, List<Rect> legend, Rect axis, Rect timeline) {
    }

    private PanelLayout() {
    }

    public static int content(int blocks) {
        return blocks * PIXELS_PER_BLOCK - 2 * MARGIN;
    }

    public static int scale(int widthBlocks, int heightBlocks, int wanted) {
        int largest = Math.min(MAX_SCALE, Math.min(content(widthBlocks), content(heightBlocks)) / CARD_WIDTH);
        return Math.clamp(wanted, 1, largest);
    }

    public static Size size(int widthBlocks, int heightBlocks, int wantedScale) {
        int scale = scale(widthBlocks, heightBlocks, wantedScale);
        return new Size(content(widthBlocks) / scale, content(heightBlocks) / scale);
    }

    public static int pixel(double blocksFromEdge, int scale) {
        return Math.floorDiv((int) Math.floor(blocksFromEdge * PIXELS_PER_BLOCK) - MARGIN, scale);
    }

    public static int rowAt(Size size, int pixelY) {
        return Math.floorDiv(pixelY, LINE) - size.headerLines();
    }

    public static int pageCount(int items, int pageSize) {
        return Math.max(1, (items + pageSize - 1) / pageSize);
    }

    public static int pageSize(PanelConfig.Mode mode, Size size, int items) {
        boolean grid = mode == PanelConfig.Mode.CARDS || mode == PanelConfig.Mode.GAUGE;
        return grid ? cards(size, items).cards().size() : list(size, items).rows().size();
    }

    public static Rect footer(PanelConfig.Mode mode, Size size, int items) {
        return switch (mode) {
            case LIST, ALARMS -> list(size, items).footer();
            case CARDS, GAUGE -> cards(size, items).footer();
            case GRAPH, HIGHLIGHT -> NONE;
        };
    }

    public static int footerStep(Rect footer, int pixelX, int pixelY) {
        if (!footer.contains(pixelX, pixelY)) {
            return 0;
        }
        return pixelX < footer.x() + footer.width() / 2 ? -1 : 1;
    }

    public static Layout list(Size size, int items) {
        int headerLines = size.headerLines();
        int available = size.lines() - headerLines;
        boolean paged = items > available && available > 1;
        int shown = paged ? available - 1 : available;
        List<Row> rows = new ArrayList<>();
        for (int line = headerLines; line < headerLines + shown; line++) {
            rows.add(row(new Rect(0, line * LINE, size.width(), LINE)));
        }
        Rect header = headerLines > 0 ? new Rect(0, 0, size.width(), LINE) : NONE;
        return new Layout(header, rows, paged ? new Rect(0, (headerLines + shown) * LINE, size.width(), LINE) : NONE);
    }

    public static Cards cards(Size size, int items) {
        int columns = size.width() / CARD_WIDTH;
        boolean paged = items > columns * (size.height() / CARD_HEIGHT);
        int height = paged ? size.height() - LINE : size.height();
        int lines = height / CARD_HEIGHT;
        int cellWidth = size.width() / columns;
        int cellHeight = height / lines;
        List<Card> cards = new ArrayList<>();
        for (int line = 0; line < lines; line++) {
            for (int column = 0; column < columns; column++) {
                cards.add(card(new Rect(column * cellWidth, line * cellHeight, cellWidth, cellHeight)));
            }
        }
        return new Cards(cards, paged ? new Rect(0, height, size.width(), LINE) : NONE);
    }

    public static Graph graph(Size size, int series) {
        boolean roomy = size.headerLines() > 0;
        int columns = Math.max(1, size.width() / LEGEND_COLUMN);
        int fitting = (size.height() - 2 * (LINE + GAP) - MIN_PLOT_LINES * LINE) / LINE + 1;
        int legendLines = roomy ? Math.clamp((series + columns - 1) / columns, 0, Math.max(1, fitting)) : 0;
        int top = legendLines > 0 ? legendLines * LINE + GAP : 0;
        int bottom = roomy ? LINE + GAP : 0;
        int left = size.width() >= NAME_WIDTH ? AXIS_WIDTH + GAP : 0;
        Rect plot = new Rect(left, top, size.width() - left, size.height() - top - bottom);
        int columnWidth = size.width() / columns;
        List<Rect> legend = new ArrayList<>();
        for (int index = 0; index < Math.min(series, legendLines * columns); index++) {
            legend.add(new Rect(index % columns * columnWidth, index / columns * LINE, columnWidth - GAP, LINE));
        }
        Rect axis = left == 0 ? NONE : new Rect(0, plot.y(), AXIS_WIDTH, plot.height());
        return new Graph(plot, legend, axis, roomy ? new Rect(left, size.height() - LINE, plot.width(), LINE) : NONE);
    }

    public static Card highlight(Size size) {
        int nameHeight = size.headerLines() * LINE;
        int height = size.height() - nameHeight;
        int side = size.width() >= NAME_WIDTH ? Math.min(height, size.width() / HIGHLIGHT_ICON_SHARE) : 0;
        int valueLeft = side > 0 ? side + 2 * GAP : 0;
        Rect icon = side > 0 ? new Rect(0, (height - side) / 2, side, side) : NONE;
        Rect name = nameHeight > 0 ? new Rect(0, height, size.width(), nameHeight) : NONE;
        return new Card(size.bounds(), icon, name, new Rect(valueLeft, 0, size.width() - valueLeft, height));
    }

    private static Card card(Rect bounds) {
        int left = bounds.x() + GAP;
        int top = bounds.y() + (bounds.height() - CARD_HEIGHT) / 2;
        int nameLeft = left + GLYPH + GAP;
        return new Card(bounds, glyphBox(left, top), new Rect(nameLeft, top, bounds.right() - GAP - nameLeft, LINE),
                new Rect(left, top + LINE, bounds.width() - 2 * GAP, CARD_VALUE_HEIGHT));
    }

    private static Row row(Rect bounds) {
        int top = bounds.y();
        Rect icon = glyphBox(0, top);
        Rect trend = bounds.width() >= TREND_WIDTH ? glyphBox(bounds.width() - GLYPH, top) : NONE;
        int left = icon.right() + GAP;
        int right = trend.empty() ? bounds.width() : trend.x() - GAP;
        int middle = right - left;
        if (bounds.width() < NAME_WIDTH) {
            return new Row(bounds, icon, NONE, new Rect(left, top, middle, LINE), NONE, trend);
        }
        if (bounds.width() < CHART_WIDTH) {
            int nameWidth = middle / 2 - GAP;
            return new Row(bounds, icon, new Rect(left, top, nameWidth, LINE),
                    new Rect(left + nameWidth + GAP, top, middle - nameWidth - GAP, LINE), NONE, trend);
        }
        int nameWidth = middle * 35 / 100 - GAP;
        int valueWidth = middle * 30 / 100 - GAP;
        int chartLeft = left + nameWidth + valueWidth + 2 * GAP;
        return new Row(bounds, icon, new Rect(left, top, nameWidth, LINE), new Rect(left + nameWidth + GAP, top, valueWidth, LINE),
                glyphRow(chartLeft, top, right - chartLeft), trend);
    }

    private static Rect glyphBox(int x, int lineTop) {
        return glyphRow(x, lineTop, GLYPH);
    }

    private static Rect glyphRow(int x, int lineTop, int width) {
        return new Rect(x, lineTop + (LINE - GLYPH) / 2, width, GLYPH);
    }
}
