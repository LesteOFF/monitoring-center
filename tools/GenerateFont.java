import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;

public final class GenerateFont {
    private static final String ASSETS = "src/main/resources/assets/monitoringcenter/";
    private static final int CELL_WIDTH = 6;
    private static final int CELL_HEIGHT = 10;
    private static final int GLYPH_WIDTH = 5;
    private static final int GLYPH_ROWS = 7;
    private static final int GLYPH_TOP = 2;
    private static final int ASCENT = 9;
    private static final int COLUMNS = 16;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int WIDTH_MARKER = 0x01FFFFFF;
    private static final char FIRST = '!';
    private static final String ACCENTED = "áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ";
    private static final String SYMBOLS = "ºª°µ";
    private static final String[] SYMBOL_GLYPHS = {"0E110E001F0000", "0E010F001F0000", "0C120C00000000", "0000111111131D"};
    private static final String[] GLYPHS = {
            "04040404040004", "0A0A0A00000000", "0A0A1F0A1F0A0A", "040F140E051E04", "18190204081303", "0C12140815120D",
            "0C040800000000", "02040808080402", "08040202020408", "0004150E150400", "0004041F040400", "000000000C0408",
            "0000001F000000", "00000000000C0C", "00010204081000", "0E11131519110E", "040C040404040E", "0E11010204081F",
            "1F02040201110E", "02060A121F0202", "1F101E0101110E", "0608101E11110E", "1F010204080808", "0E11110E11110E",
            "0E11110F01020C", "000C0C000C0C00", "000C0C000C0408", "02040810080402", "00001F001F0000", "08040201020408",
            "0E110102040004", "0E11010D15150E", "0E1111111F1111", "1E11111E11111E", "0E11101010110E", "1C12111111121C",
            "1F10101E10101F", "1F10101E101010", "0E11101711110F", "1111111F111111", "0E04040404040E", "0702020202120C",
            "11121418141211", "1010101010101F", "111B1515111111", "11111915131111", "0E11111111110E", "1E11111E101010",
            "0E11111115120D", "1E11111E141211", "0F10100E01011E", "1F040404040404", "1111111111110E", "11111111110A04",
            "1111111515150A", "11110A040A1111", "1111110A040404", "1F01020408101F", "0E08080808080E", "00100804020100",
            "0E02020202020E", "040A1100000000", "0000000000001F", "08040200000000", "00000E010F110F", "1010161911111E",
            "00000E1010110E", "01010D1311110F", "00000E111F100E", "0609081C080808", "000F11110F010E", "10101619111111",
            "04000C0404040E", "0200060202120C", "10101214181412", "0C04040404040E", "00001A15151111", "00001619111111",
            "00000E1111110E", "00001E111E1010", "00000D130F0101", "00001619101010", "00000E100E011E", "08081C08080906",
            "0000111111130D", "00001111110A04", "0000111115150A", "0000110A040A11", "000011110F010E", "00001F0204081F",
            "02040408040402", "04040404040404", "08040402040408", "00000815020000"};

    public static void main(String[] args) throws IOException {
        StringBuilder all = new StringBuilder();
        for (char c = FIRST; c <= '~'; c++) {
            all.append(c);
        }
        all.append(ACCENTED).append(SYMBOLS);
        int lines = (all.length() + COLUMNS - 1) / COLUMNS;
        BufferedImage sheet = new BufferedImage(CELL_WIDTH * COLUMNS, CELL_HEIGHT * lines, BufferedImage.TYPE_INT_ARGB);
        StringBuilder json = new StringBuilder("{\n  \"providers\": [\n    {\"type\": \"space\", \"advances\": {\" \": 4}},\n")
                .append("    {\"type\": \"bitmap\", \"file\": \"monitoringcenter:font/panel.png\", \"height\": ").append(CELL_HEIGHT)
                .append(", \"ascent\": ").append(ASCENT).append(", \"chars\": [\n");
        for (int line = 0; line < lines; line++) {
            json.append("      \"");
            for (int column = 0; column < COLUMNS; column++) {
                int index = line * COLUMNS + column;
                char c = index < all.length() ? all.charAt(index) : 0;
                json.append(String.format("\\u%04x", (int) c));
                if (c != 0) {
                    draw(sheet, column * CELL_WIDTH, line * CELL_HEIGHT, c);
                }
            }
            json.append(line + 1 < lines ? "\",\n" : "\"\n");
        }
        json.append("    ]}\n  ]\n}\n");
        File image = new File(ASSETS + "textures/font/panel.png");
        image.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", image);
        File font = new File(ASSETS + "font/panel.json");
        font.getParentFile().mkdirs();
        Files.writeString(font.toPath(), json, StandardCharsets.UTF_8);
    }

    private static void draw(BufferedImage sheet, int x, int y, char c) {
        int symbol = SYMBOLS.indexOf(c);
        if (symbol >= 0) {
            rows(sheet, x, y + GLYPH_TOP, SYMBOL_GLYPHS[symbol], 0);
            return;
        }
        String parts = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        char base = parts.charAt(0);
        boolean accented = parts.length() > 1;
        boolean lower = Character.isLowerCase(base);
        rows(sheet, x, y + GLYPH_TOP, GLYPHS[base - FIRST], accented && lower && parts.charAt(1) != '̧' ? 2 : 0);
        if (accented) {
            accent(sheet, x, y + (lower ? GLYPH_TOP : 0), parts.charAt(1));
        }
        if (Character.isDigit(c)) {
            sheet.setRGB(x + GLYPH_WIDTH - 1, y + GLYPH_TOP, sheet.getRGB(x + GLYPH_WIDTH - 1, y + GLYPH_TOP) | WIDTH_MARKER);
        }
    }

    private static void accent(BufferedImage sheet, int x, int y, char mark) {
        switch (mark) {
            case '́' -> rows(sheet, x, y, "0204", 0);
            case '̀' -> rows(sheet, x, y, "0804", 0);
            case '̂' -> rows(sheet, x, y, "040A", 0);
            case '̃' -> rows(sheet, x, y, "0D12", 0);
            default -> sheet.setRGB(x + 2, y + (CELL_HEIGHT - 1 - (y % CELL_HEIGHT)), WHITE);
        }
    }

    private static void rows(BufferedImage sheet, int x, int y, String hex, int skippedRows) {
        for (int row = skippedRows; row < hex.length() / 2; row++) {
            int bits = Integer.parseInt(hex.substring(row * 2, row * 2 + 2), 16);
            for (int column = 0; column < GLYPH_WIDTH; column++) {
                if ((bits >> (GLYPH_WIDTH - 1 - column) & 1) != 0) {
                    sheet.setRGB(x + column, y + row, WHITE);
                }
            }
        }
    }
}
