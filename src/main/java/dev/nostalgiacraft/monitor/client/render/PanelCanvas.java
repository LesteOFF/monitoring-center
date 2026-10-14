package dev.nostalgiacraft.monitor.client.render;

import dev.nostalgiacraft.monitor.client.render.PanelMesh.Quad;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Rect;
import dev.nostalgiacraft.monitor.series.RingBuffer;

import java.util.List;

final class PanelCanvas {
    private static final int TRACK = 0x30FFFFFF;
    private static final int BAR_HEIGHT = 4;

    enum Align {
        LEFT, CENTER, RIGHT
    }

    private final PanelMesh mesh = new PanelMesh();
    private final PanelFontAtlas font = PanelFont.atlas();

    PanelMesh mesh() {
        return mesh;
    }

    float width(String content, float scale) {
        return font.width(content) * scale;
    }

    int fittingScale(String content, Rect area, int largest) {
        int scale = Math.max(1, Math.min(largest, area.height() / PanelLayout.GLYPH));
        while (scale > 1 && width(content, scale) > area.width()) {
            scale--;
        }
        return scale;
    }

    void text(String content, float x, float y, float scale, int color) {
        float cursor = x;
        for (int index = 0; index < content.length(); ) {
            int codepoint = content.codePointAt(index);
            PanelFontAtlas.Glyph glyph = font.glyph(codepoint);
            if (glyph.visible()) {
                float top = y + glyph.top() * scale;
                float size = glyph.scale() * scale;
                mesh.glyphs.add(new Quad(cursor, top, cursor + glyph.width() * size, top + glyph.height() * size,
                        glyph.x() / (float) font.width(), glyph.y() / (float) font.height(),
                        (glyph.x() + glyph.width()) / (float) font.width(), (glyph.y() + glyph.height()) / (float) font.height(), color));
            }
            cursor += glyph.advance() * scale;
            index += Character.charCount(codepoint);
        }
    }

    void textIn(String content, Rect area, Align align, float scale, int color) {
        String shown = font.clipped(content, area.width() / scale);
        float free = area.width() - width(shown, scale);
        float x = area.x() + switch (align) {
            case LEFT -> 0f;
            case CENTER -> free / 2f;
            case RIGHT -> free;
        };
        text(shown, x, area.y() + (area.height() - PanelLayout.GLYPH * scale) / 2f, scale, color);
    }

    void fill(float x0, float y0, float x1, float y1, int color) {
        mesh.fills.add(new Quad(x0, y0, x1, y1, 0f, 0f, 0f, 0f, color));
    }

    void icon(Rect area, float u0, float v0, float u1, float v1, int color) {
        mesh.icons.add(new Quad(area.x(), area.y(), area.right(), area.bottom(), u0, v0, u1, v1, color));
    }

    void model(String icon, Rect area) {
        mesh.models.add(new PanelMesh.Model(icon, area));
    }

    void bar(Rect area, double fraction, int color) {
        float top = track(area);
        if (!Double.isNaN(fraction)) {
            fill(area.x(), top, area.x() + (float) (area.width() * Math.clamp(fraction, 0d, 1d)), top + BAR_HEIGHT, color);
        }
    }

    void stack(Rect area, List<Double> shares, int[] colors) {
        float top = track(area);
        float left = area.x();
        for (int index = 0; index < shares.size() && left < area.right(); index++) {
            float right = Math.min(area.right(), left + (float) (area.width() * shares.get(index)));
            fill(left, top, right, top + BAR_HEIGHT, colors[index % colors.length]);
            left = right;
        }
    }

    private float track(Rect area) {
        float top = area.y() + (area.height() - BAR_HEIGHT) / 2f;
        fill(area.x(), top, area.right(), top + BAR_HEIGHT, TRACK);
        return top;
    }

    void line(Rect area, RingBuffer samples, int color) {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (int index = 0; index < samples.size(); index++) {
            float sample = samples.get(index);
            if (!Float.isNaN(sample)) {
                lowest = Math.min(lowest, sample);
                highest = Math.max(highest, sample);
            }
        }
        line(area, samples, color, 1, lowest, highest);
    }

    void line(Rect area, RingBuffer samples, int color, int thickness, float low, float high) {
        if (samples.size() < 2 || area.empty()) {
            return;
        }
        float range = high - low;
        int usable = area.height() - thickness;
        int previousY = Integer.MIN_VALUE;
        for (int column = 0; column < area.width(); column++) {
            float sample = samples.get(column * samples.size() / area.width());
            if (Float.isNaN(sample)) {
                previousY = Integer.MIN_VALUE;
                continue;
            }
            int y = area.y() + (range <= 0 ? usable / 2 : Math.round(usable * (1f - Math.clamp((sample - low) / range, 0f, 1f))));
            int from = previousY == Integer.MIN_VALUE ? y : Math.min(y, previousY);
            int to = previousY == Integer.MIN_VALUE ? y : Math.max(y, previousY);
            fill(area.x() + column, from, area.x() + column + 1, to + thickness, color);
            previousY = y;
        }
    }
}
