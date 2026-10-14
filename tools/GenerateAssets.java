import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public final class GenerateAssets {
    private static final String ASSETS = "src/main/resources/assets/monitoringcenter/";
    private static final int ICON = 16;
    private static final int ICON_COLUMNS = 8;

    public static void main(String[] args) throws IOException {
        icons();
    }

    private static void icons() throws IOException {
        List<Consumer<Graphics2D>> glyphs = List.of(
                g -> g.fillPolygon(xs(9, 4, 8, 6, 12, 8, 10), xs(1, 9, 9, 15, 6, 6, 1), 7),
                g -> { g.fillOval(3, 7, 10, 8); g.fillPolygon(xs(8, 4, 12), xs(1, 10, 10), 3); },
                g -> { g.drawRect(2, 4, 11, 9); g.drawLine(2, 7, 13, 7); g.fillRect(7, 6, 2, 3); },
                g -> { g.fillOval(5, 5, 6, 6); dots(g, 2, 2, 12, 2, 2, 12, 12, 12, 8, 1, 8, 14, 1, 8, 14, 8); },
                g -> { g.fillOval(3, 3, 10, 10); g.fillRect(7, 1, 2, 14); g.fillRect(1, 7, 14, 2); dots(g, 3, 3, 12, 3, 3, 12, 12, 12);
                    clear(g, () -> g.fillOval(6, 6, 4, 4)); },
                g -> { g.drawArc(2, 3, 11, 11, 0, 180); g.drawLine(2, 9, 13, 9); g.drawLine(8, 9, 11, 5); g.fillRect(7, 8, 2, 2); },
                g -> { g.fillRect(7, 1, 2, 10); g.fillOval(5, 9, 6, 6); },
                g -> { g.fillArc(1, 1, 14, 14, 60, 60); g.fillArc(1, 1, 14, 14, 180, 60); g.fillArc(1, 1, 14, 14, 300, 60);
                    clear(g, () -> g.fillOval(5, 5, 6, 6)); g.fillOval(7, 7, 2, 2); },
                g -> { g.drawOval(2, 2, 11, 11); g.drawLine(8, 8, 8, 4); g.drawLine(8, 8, 11, 8); },
                g -> { g.fillOval(2, 3, 6, 6); g.fillOval(8, 3, 6, 6); g.fillPolygon(xs(2, 14, 8), xs(7, 7, 14), 3); },
                g -> { g.fillOval(5, 1, 6, 6); g.fillRoundRect(3, 8, 10, 7, 4, 4); },
                g -> { g.fillOval(5, 5, 6, 6); g.drawLine(8, 1, 8, 3); g.drawLine(8, 13, 8, 15); g.drawLine(1, 8, 3, 8); g.drawLine(13, 8, 15, 8);
                    g.drawLine(3, 3, 4, 4); g.drawLine(12, 12, 13, 13); g.drawLine(3, 13, 4, 12); g.drawLine(12, 4, 13, 3); },
                g -> { g.fillOval(2, 7, 7, 6); g.fillOval(6, 4, 7, 8); g.fillOval(9, 8, 5, 5); g.fillRect(4, 10, 9, 3); },
                g -> { g.fillPolygon(xs(3, 3, 8, 13, 13, 8), xs(13, 7, 2, 2, 7, 13), 6); clear(g, () -> g.drawLine(4, 12, 11, 5)); },
                g -> { g.fillRoundRect(3, 2, 10, 9, 4, 4); g.fillRect(5, 11, 6, 3);
                    clear(g, () -> { g.fillRect(5, 5, 2, 3); g.fillRect(9, 5, 2, 3); g.fillRect(7, 12, 1, 2); }); },
                g -> { g.fillRect(2, 10, 5, 5); g.fillRect(9, 10, 5, 5); g.fillRect(5, 4, 5, 5); },
                g -> { g.drawRect(4, 4, 7, 7); g.fillRect(6, 6, 4, 4);
                    for (int pin = 5; pin <= 10; pin += 2) { g.drawLine(pin, 2, pin, 3); g.drawLine(pin, 12, pin, 13);
                        g.drawLine(2, pin, 3, pin); g.drawLine(12, pin, 13, pin); } },
                g -> { g.fillRect(6, 1, 4, 4); g.fillRect(1, 11, 4, 4); g.fillRect(11, 11, 4, 4); g.drawLine(8, 5, 8, 8);
                    g.drawLine(3, 8, 13, 8); g.drawLine(3, 8, 3, 11); g.drawLine(13, 8, 13, 11); },
                g -> { g.drawPolygon(xs(6, 9, 9, 13, 2, 6), xs(1, 1, 6, 14, 14, 6), 6); g.fillPolygon(xs(4, 11, 13, 2), xs(10, 10, 14, 14), 4); },
                g -> g.fillPolygon(xs(8, 10, 15, 11, 13, 8, 3, 5, 1, 6), xs(1, 6, 6, 9, 15, 11, 15, 9, 6, 6), 10),
                g -> { g.drawRect(2, 2, 11, 4); g.drawRect(2, 9, 11, 4); dots(g, 4, 4, 4, 11); g.drawLine(8, 4, 11, 4); g.drawLine(8, 11, 11, 11); },
                g -> g.drawPolyline(xs(1, 4, 6, 9, 11, 15), xs(8, 8, 3, 13, 8, 8), 6),
                g -> { g.fillPolygon(xs(8, 15, 1), xs(1, 14, 14), 3); clear(g, () -> { g.fillRect(7, 5, 2, 5); g.fillRect(7, 11, 2, 2); }); },
                g -> { g.fillArc(3, 2, 10, 12, 0, 180); g.fillRect(3, 8, 10, 4); g.fillRect(2, 12, 12, 1); g.fillRect(7, 13, 2, 2); },
                g -> { g.drawRect(2, 4, 11, 8); g.fillRect(14, 6, 1, 4); g.fillRect(4, 6, 5, 5); },
                g -> { g.fillPolygon(xs(8, 12, 12, 10, 6, 4, 4, 6, 7), xs(1, 7, 11, 14, 14, 11, 8, 5, 8), 9); clear(g, () -> g.fillRect(7, 10, 2, 3)); },
                g -> g.fillPolygon(xs(8, 14, 10, 10, 6, 6, 2), xs(2, 9, 9, 14, 14, 9, 9), 7),
                g -> g.fillPolygon(xs(8, 14, 10, 10, 6, 6, 2), xs(14, 7, 7, 2, 2, 7, 7), 7),
                g -> g.fillPolygon(xs(14, 8, 8, 2, 2, 8, 8), xs(8, 2, 6, 6, 10, 10, 14), 7),
                g -> { g.setStroke(new BasicStroke(2)); g.drawPolyline(xs(12, 4, 8, 4, 12), xs(3, 3, 8, 13, 13), 5); },
                g -> { g.fillOval(3, 2, 11, 12); clear(g, () -> g.fillOval(6, 0, 10, 10)); },
                g -> { g.drawPolyline(xs(5, 7, 10, 12, 12, 8, 8), xs(4, 2, 2, 4, 6, 9, 11), 7); g.fillRect(7, 13, 2, 2); });
        int rows = (glyphs.size() + ICON_COLUMNS - 1) / ICON_COLUMNS;
        BufferedImage atlas = new BufferedImage(ICON * ICON_COLUMNS, ICON * rows, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < glyphs.size(); index++) {
            Graphics2D cell = (Graphics2D) atlas.createGraphics().create(index % ICON_COLUMNS * ICON, index / ICON_COLUMNS * ICON, ICON, ICON);
            cell.setColor(Color.WHITE);
            glyphs.get(index).accept(cell);
            cell.dispose();
        }
        write(atlas, "textures/panel/icons.png");
    }

    private static int[] xs(int... values) {
        return values;
    }

    private static void dots(Graphics2D graphics, int... points) {
        for (int index = 0; index < points.length; index += 2) {
            graphics.fillRect(points[index], points[index + 1], 2, 2);
        }
    }

    private static void clear(Graphics2D graphics, Runnable shape) {
        graphics.setComposite(AlphaComposite.Clear);
        shape.run();
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static void write(BufferedImage image, String path) throws IOException {
        File file = new File(ASSETS + path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
