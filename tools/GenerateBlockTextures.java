import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class GenerateBlockTextures {
    private static final Path TEXTURES = Path.of("src/main/resources/assets/monitoringcenter/textures/block");
    private static final int CELL = 16;
    private static final int PANEL = 32;
    private static final int CASE = 0xFF2B2B2B;
    private static final int CASE_LIGHT = 0xFF4A4A4A;
    private static final int CASE_SHADE = 0xFF161616;
    private static final int FRAME = 0xFFC5C5C5;
    private static final int FRAME_LIGHT = 0xFFFEFEFE;
    private static final int FRAME_MID = 0xFF8E8E8E;
    private static final int FRAME_SHADE = 0xFF545454;
    private static final int GLASS = 0xFF0D0F12;
    private static final int AMBER = 0xFFFFB000;
    private static final int AMBER_DIM = 0xFF8A5F00;
    private static final int GREEN = 0xFF6BFF95;
    private static final int RED = 0xFFFF3B30;
    private static final int NOISE = 3;

    private final BufferedImage image;

    private GenerateBlockTextures(int size) {
        image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }

    public static void main(String[] args) throws IOException {
        central().save("central");
        sensor().save("sensor");
        screen().save("screen");
    }

    private static GenerateBlockTextures central() {
        GenerateBlockTextures art = new GenerateBlockTextures(2 * CELL);
        art.framed(0, 0, CELL);
        art.fill(3, 3, 13, 9, GLASS);
        int[] trace = {7, 7, 6, 6, 5, 6, 4, 5, 4};
        for (int step = 0; step < trace.length; step++) {
            art.dot(4 + step, trace[step], AMBER);
        }
        art.dot(4, 11, AMBER);
        art.dot(6, 11, GREEN);
        art.dot(8, 11, RED);
        art.slots(10, 10, 13, 13);
        art.framed(0, CELL, CELL);
        art.slots(4, CELL + 4, 12, CELL + 12);
        art.framed(CELL, 0, CELL);
        art.raised(CELL + 5, 5, CELL + 11, 11);
        art.dot(CELL + 7, 7, AMBER_DIM);
        art.dot(CELL + 8, 8, AMBER_DIM);
        art.casing(CELL, CELL, CELL, CELL);
        return art;
    }

    private static GenerateBlockTextures sensor() {
        GenerateBlockTextures art = new GenerateBlockTextures(2 * CELL);
        art.framed(0, 0, CELL);
        art.fill(4, 4, 12, 12, FRAME_MID);
        art.fill(5, 5, 11, 11, GLASS);
        art.fill(7, 7, 9, 9, AMBER);
        art.dot(6, 6, AMBER_DIM);
        art.dot(13, 2, GREEN);
        art.casing(0, CELL, CELL, CELL);
        art.fill(0, CELL + 7, CELL, CELL + 9, FRAME_MID);
        art.metal(CELL, 0);
        art.metal(CELL, CELL);
        return art;
    }

    private static GenerateBlockTextures screen() {
        GenerateBlockTextures art = new GenerateBlockTextures(2 * PANEL);
        art.fill(0, 0, PANEL, PANEL, GLASS);
        art.fill(0, 0, PANEL, 1, FRAME);
        art.fill(0, 0, 1, PANEL, FRAME);
        art.fill(0, PANEL - 1, PANEL, PANEL, FRAME_SHADE);
        art.fill(PANEL - 1, 0, PANEL, PANEL, FRAME_SHADE);
        art.dot(0, 0, FRAME_LIGHT);
        art.casing(PANEL, 0, PANEL, PANEL);
        art.casing(PANEL, PANEL, PANEL, PANEL);
        art.casing(0, PANEL, PANEL, PANEL);
        art.slots(8, PANEL + 8, 24, PANEL + 24);
        return art;
    }

    private void framed(int left, int top, int size) {
        casing(left, top, size, size);
        int last = size - 1;
        for (int step = 0; step < size; step++) {
            dot(left + step, top, FRAME);
            dot(left, top + step, FRAME);
            dot(left + step, top + last, FRAME_SHADE);
            dot(left + last, top + step, FRAME_SHADE);
        }
        for (int step = 1; step < last; step++) {
            dot(left + step, top + 1, FRAME_MID);
            dot(left + 1, top + step, FRAME_MID);
            dot(left + step, top + last - 1, CASE_SHADE);
            dot(left + last - 1, top + step, CASE_SHADE);
        }
        dot(left, top, FRAME_LIGHT);
    }

    private void casing(int left, int top, int width, int height) {
        for (int y = top; y < top + height; y++) {
            for (int x = left; x < left + width; x++) {
                boolean lit = x == left || y == top;
                boolean shaded = x == left + width - 1 || y == top + height - 1;
                dot(x, y, shaded ? CASE_SHADE : lit ? CASE_LIGHT : speckled(CASE, x, y));
            }
        }
    }

    private void metal(int left, int top) {
        for (int y = top; y < top + CELL; y++) {
            for (int x = left; x < left + CELL; x++) {
                boolean lit = x == left || y == top;
                boolean shaded = x == left + CELL - 1 || y == top + CELL - 1;
                dot(x, y, shaded ? FRAME_SHADE : lit ? FRAME : speckled(FRAME_MID, x, y));
            }
        }
    }

    private void raised(int left, int top, int right, int bottom) {
        fill(left, top, right, bottom, CASE_LIGHT);
        fill(left + 1, top + 1, right, bottom, CASE_SHADE);
        fill(left + 1, top + 1, right - 1, bottom - 1, CASE);
    }

    private void slots(int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y += 2) {
            fill(left, y, right, y + 1, CASE_SHADE);
            fill(left, y + 1, right, y + 2, CASE_LIGHT);
        }
    }

    private void fill(int left, int top, int right, int bottom, int color) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                dot(x, y, color);
            }
        }
    }

    private void dot(int x, int y, int color) {
        image.setRGB(x, y, color);
    }

    private static int speckled(int color, int x, int y) {
        int shift = Math.floorMod(x * 31 + y * 17 + x * y * 7, 2 * NOISE + 1) - NOISE;
        int red = Math.clamp((color >> 16 & 0xFF) + shift, 0, 255);
        int green = Math.clamp((color >> 8 & 0xFF) + shift, 0, 255);
        int blue = Math.clamp((color & 0xFF) + shift, 0, 255);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private void save(String name) throws IOException {
        ImageIO.write(image, "png", TEXTURES.resolve(name + ".png").toFile());
    }
}
