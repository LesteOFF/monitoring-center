package dev.nostalgiacraft.monitor.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Metrics for the same bitmap used to paint the panel, independent of Minecraft's world font atlas. */
final class PanelFontAtlas {
    record Glyph(int x, int y, int width, int height, float top, float scale, float advance) {
        boolean visible() {
            return width > 0 && height > 0;
        }
    }

    private final Map<Integer, Glyph> glyphs;
    private final int width;
    private final int height;

    private PanelFontAtlas(Map<Integer, Glyph> glyphs, int width, int height) {
        this.glyphs = Map.copyOf(glyphs);
        this.width = width;
        this.height = height;
    }

    static PanelFontAtlas read(Reader definition, BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Panel font bitmap could not be decoded");
        }
        Map<Integer, Glyph> glyphs = new HashMap<>();
        JsonArray providers = JsonParser.parseReader(definition).getAsJsonObject().getAsJsonArray("providers");
        for (JsonElement element : providers) {
            JsonObject provider = element.getAsJsonObject();
            switch (provider.get("type").getAsString()) {
                case "space" -> provider.getAsJsonObject("advances").entrySet().forEach(space ->
                        glyphs.put(space.getKey().codePointAt(0), new Glyph(0, 0, 0, 0, 0, 1, space.getValue().getAsFloat())));
                case "bitmap" -> bitmap(glyphs, provider, image);
                default -> throw new IllegalArgumentException("Unsupported panel font provider: " + provider.get("type"));
            }
        }
        if (!glyphs.containsKey((int) '?')) {
            throw new IllegalArgumentException("Panel bitmap font must contain the fallback '?' glyph");
        }
        return new PanelFontAtlas(glyphs, image.getWidth(), image.getHeight());
    }

    private static void bitmap(Map<Integer, Glyph> glyphs, JsonObject provider, BufferedImage image) {
        JsonArray rows = provider.getAsJsonArray("chars");
        int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
        if (columns == 0 || image.getWidth() % columns != 0 || image.getHeight() % rows.size() != 0) {
            throw new IllegalArgumentException("Panel font bitmap dimensions do not match its character grid");
        }
        int cellWidth = image.getWidth() / columns;
        int cellHeight = image.getHeight() / rows.size();
        int renderedHeight = provider.has("height") ? provider.get("height").getAsInt() : 8;
        float scale = renderedHeight / (float) cellHeight;
        float top = 7 - provider.get("ascent").getAsInt();
        for (int row = 0; row < rows.size(); row++) {
            int[] characters = rows.get(row).getAsString().codePoints().toArray();
            if (characters.length != columns) {
                throw new IllegalArgumentException("Panel font character rows must have equal lengths");
            }
            for (int column = 0; column < columns; column++) {
                int codepoint = characters[column];
                if (codepoint == 0) {
                    continue;
                }
                int x = column * cellWidth;
                int y = row * cellHeight;
                int inkWidth = 0;
                for (int px = cellWidth - 1; px >= 0 && inkWidth == 0; px--) {
                    for (int py = 0; py < cellHeight; py++) {
                        if ((image.getRGB(x + px, y + py) >>> 24) != 0) {
                            inkWidth = px + 1;
                            break;
                        }
                    }
                }
                // Minecraft's bitmap provider rounds the ink width, then adds one pixel of spacing.
                float advance = (int) (0.5f + inkWidth * scale) + 1;
                glyphs.put(codepoint, new Glyph(x, y, inkWidth == 0 ? 0 : cellWidth, cellHeight, top, scale, advance));
            }
        }
    }

    Glyph glyph(int codepoint) {
        return glyphs.getOrDefault(codepoint, glyphs.get((int) '?'));
    }

    float width(String text) {
        return (float) text.codePoints().mapToDouble(codepoint -> glyph(codepoint).advance()).sum();
    }

    String clipped(String text, float available) {
        float used = 0;
        int end = 0;
        while (end < text.length()) {
            int codepoint = text.codePointAt(end);
            float advance = glyph(codepoint).advance();
            if (used + advance > available) {
                break;
            }
            used += advance;
            end += Character.charCount(codepoint);
        }
        return text.substring(0, end);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }
}
