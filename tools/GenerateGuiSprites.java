import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GenerateGuiSprites {
    private static final Path SPRITES = Path.of("src/main/resources/assets/monitoringcenter/textures/gui/sprites/workshop");
    private static final int SIZE = 16;
    private static final int EDGE = 3;
    private static final int FRAME_WIDTH = 4;
    private static final int FRAME = 0xFFC5C5C5;
    private static final int FRAME_LIGHT = 0xFFFEFEFE;
    private static final int FRAME_SHADE = 0xFF545454;
    private static final int CASE = 0xFF232323;
    private static final int BLACK = 0xFF000000;
    private static final int TERMINAL = 0xFF111111;
    private static final int TERMINAL_SHADE = 0xFF080808;
    private static final int TERMINAL_EDGE = 0xFF3A3A3A;
    private static final int FIELD_FILL = 0xFF0A0A0A;
    private static final int FIELD_OUTLINE = 0xFF5A5A5A;
    private static final int AMBER = 0xFFFFB000;
    private static final int AMBER_LIGHT = 0xFFFFD060;
    private static final int AMBER_SHADE = 0xFFB37B00;
    private static final int AMBER_FADED = 0xFF6B5A2A;
    private static final int AMBER_FADED_LIGHT = 0xFF7D6B38;
    private static final int AMBER_FADED_SHADE = 0xFF54461F;
    private static final int KEY = 0xFF3A3A3A;
    private static final int KEY_LIGHT = 0xFF5A5A5A;
    private static final int KEY_SHADE = 0xFF1A1A1A;
    private static final int KEY_HOVER = 0xFF4C4C4C;
    private static final int KEY_HOVER_LIGHT = 0xFF6E6E6E;

    public static void main(String[] args) throws IOException {
        Files.createDirectories(SPRITES);
        write("window", window(), FRAME_WIDTH);
        write("content", raised(BLACK, TERMINAL, TERMINAL_SHADE, TERMINAL_EDGE), EDGE);
        write("field", raised(FIELD_OUTLINE, FIELD_FILL, BLACK, FIELD_FILL), EDGE);
        write("field_focused", raised(AMBER, FIELD_FILL, BLACK, FIELD_FILL), EDGE);
        write("button", raised(BLACK, KEY, KEY_LIGHT, KEY_SHADE), EDGE);
        write("button_hover", raised(AMBER, KEY_HOVER, KEY_HOVER_LIGHT, KEY_SHADE), EDGE);
        write("button_primary", raised(BLACK, AMBER, AMBER_LIGHT, AMBER_SHADE), EDGE);
        write("button_primary_hover", raised(FRAME_LIGHT, AMBER_LIGHT, FRAME_LIGHT, AMBER), EDGE);
        write("button_primary_disabled", raised(BLACK, AMBER_FADED, AMBER_FADED_LIGHT, AMBER_FADED_SHADE), EDGE);
    }

    private static BufferedImage raised(int outline, int fill, int topLeft, int bottomRight) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int last = SIZE - 1;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean border = x == 0 || y == 0 || x == last || y == last;
                boolean lit = x == 1 || y == 1;
                boolean shaded = x == last - 1 || y == last - 1;
                image.setRGB(x, y, border ? outline : shaded ? bottomRight : lit ? topLeft : fill);
            }
        }
        return image;
    }

    private static BufferedImage window() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int last = SIZE - 1;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int depth = Math.min(Math.min(x, y), Math.min(last - x, last - y));
                boolean topLeft = Math.min(x, y) == depth;
                int rim = topLeft ? FRAME_LIGHT : FRAME_SHADE;
                int lip = topLeft ? FRAME_SHADE : FRAME_LIGHT;
                image.setRGB(x, y, depth == 0 ? rim : depth < FRAME_WIDTH - 1 ? FRAME : depth == FRAME_WIDTH - 1 ? lip : CASE);
            }
        }
        return image;
    }

    private static void write(String name, BufferedImage image, int border) throws IOException {
        ImageIO.write(image, "png", SPRITES.resolve(name + ".png").toFile());
        Files.writeString(SPRITES.resolve(name + ".png.mcmeta"), """
                {
                  "gui": {
                    "scaling": {
                      "type": "nine_slice",
                      "width": %d,
                      "height": %d,
                      "border": %d
                    }
                  }
                }
                """.formatted(SIZE, SIZE, border));
    }
}
