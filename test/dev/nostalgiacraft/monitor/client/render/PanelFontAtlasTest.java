package dev.nostalgiacraft.monitor.client.render;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PanelFontAtlasTest {
    private static PanelFontAtlas panelFont() throws Exception {
        ClassLoader resources = PanelFontAtlasTest.class.getClassLoader();
        try (var definition = resources.getResourceAsStream("assets/monitoringcenter/font/panel.json");
             var image = resources.getResourceAsStream("assets/monitoringcenter/textures/font/panel.png")) {
            assertNotNull(definition);
            assertNotNull(image);
            return PanelFontAtlas.read(new InputStreamReader(definition, StandardCharsets.UTF_8), ImageIO.read(image));
        }
    }

    @Test
    void fontKeepsMonospacedDigitsForRollingValues() throws Exception {
        PanelFontAtlas font = panelFont();
        float advance = font.glyph('0').advance();
        for (char digit = '1'; digit <= '9'; digit++) {
            assertEquals(advance, font.glyph(digit).advance(), "digit " + digit);
        }
        assertEquals(font.width("100000"), font.width("999999"));
    }

    @Test
    void portugueseLabelsHaveRealVisibleGlyphs() throws Exception {
        PanelFontAtlas font = panelFont();
        for (int codepoint : "Sem energiaConexãoÁÉÍÓÚÇáàâãéêíóôõúçºª°µ".codePoints().toArray()) {
            if (codepoint != ' ') {
                assertTrue(font.glyph(codepoint).visible());
                assertNotEquals(font.glyph('?'), font.glyph(codepoint));
            }
        }
        assertEquals(4f, font.width(" "));
    }

    @Test
    void clippingUsesTheSameMetricsAndPreservesUnicodeCodepoints() throws Exception {
        PanelFontAtlas font = panelFont();
        assertEquals("Energia", font.clipped("Energia FE", font.width("Energia")));
        assertEquals("", font.clipped("A", font.width("A") - 1));
        assertEquals("A\uD83D\uDE00", font.clipped("A\uD83D\uDE00B", font.width("A?")));
        assertEquals(font.width("?"), font.width("\uD83D\uDE00"));
    }

    @Test
    void everyIconCellHasInkAndATransparentBackground() throws Exception {
        try (var data = getClass().getClassLoader().getResourceAsStream("assets/monitoringcenter/textures/panel/icons.png")) {
            assertNotNull(data);
            BufferedImage icons = ImageIO.read(data);
            assertEquals(128, icons.getWidth());
            assertEquals(64, icons.getHeight());
            for (int cell = 0; cell < 32; cell++) {
                boolean ink = false;
                boolean clear = false;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int alpha = icons.getRGB(cell % 8 * 16 + x, cell / 8 * 16 + y) >>> 24;
                        ink |= alpha > 0;
                        clear |= alpha == 0;
                    }
                }
                assertTrue(ink, "empty icon cell " + cell);
                assertTrue(clear, "opaque rectangle in icon cell " + cell);
            }
        }
    }
}
