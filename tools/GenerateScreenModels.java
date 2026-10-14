import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GenerateScreenModels {
    private static final Path ASSETS = Path.of("src/main/resources/assets/monitoringcenter");
    private static final String TEXTURE = "monitoringcenter:block/screen";
    private static final int FACE_TEXELS = 32;
    private static final int LAST = FACE_TEXELS - 1;
    private static final double TEXELS_PER_UNIT = 2;
    private static final double TEXELS_PER_UV = 4;
    private static final int FRONT = 13;
    private static final String[][] FACINGS = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};

    private record Span(int from, int to) {
    }

    private record Side(String name, boolean horizontal, Span across, Span frame, Span glass) {
    }

    private static final Span MIDDLE = new Span(1, LAST);
    private static final List<Side> SIDES = List.of(
            new Side("left", true, MIDDLE, new Span(0, 1), new Span(1, 2)),
            new Side("right", true, MIDDLE, new Span(LAST, FACE_TEXELS), new Span(LAST - 1, LAST)),
            new Side("up", false, MIDDLE, new Span(0, 1), new Span(1, 2)),
            new Side("down", false, MIDDLE, new Span(LAST, FACE_TEXELS), new Span(LAST - 1, LAST)));

    public static void main(String[] args) throws IOException {
        write("screen_body", body());
        List<String[]> pieces = new ArrayList<>();
        pieces.add(new String[]{"", "screen_body"});
        for (Side side : SIDES) {
            for (boolean connected : new boolean[]{false, true}) {
                String name = "screen_edge_" + side.name() + (connected ? "_glass" : "_frame");
                Span source = connected ? side.glass() : side.frame();
                write(name, side.horizontal() ? piece(side.frame(), side.across(), source, side.across())
                        : piece(side.across(), side.frame(), side.across(), source));
                pieces.add(new String[]{"\"" + side.name() + "\": \"" + connected + "\"", name});
            }
        }
        for (Side column : SIDES.subList(0, 2)) {
            for (Side row : SIDES.subList(2, 4)) {
                for (boolean beside : new boolean[]{false, true}) {
                    for (boolean above : new boolean[]{false, true}) {
                        String name = "screen_corner_" + column.name() + "_" + row.name() + "_" + (beside ? "open" : "shut") + "_" + (above ? "open" : "shut");
                        write(name, piece(column.frame(), row.frame(), beside ? column.glass() : column.frame(), above ? row.glass() : row.frame()));
                        pieces.add(new String[]{"\"" + column.name() + "\": \"" + beside + "\", \"" + row.name() + "\": \"" + above + "\"", name});
                    }
                }
            }
        }
        Files.writeString(ASSETS.resolve("blockstates/screen.json"), blockstate(pieces));
    }

    private static String blockstate(List<String[]> pieces) {
        List<String> parts = new ArrayList<>();
        for (String[] facing : FACINGS) {
            for (String[] piece : pieces) {
                String when = "\"facing\": \"" + facing[0] + "\"" + (piece[0].isEmpty() ? "" : ", " + piece[0]);
                String rotation = facing[1].equals("0") ? "" : ", \"y\": " + facing[1];
                parts.add("    {\"when\": {" + when + "}, \"apply\": {\"model\": \"monitoringcenter:block/" + piece[1] + "\"" + rotation + "}}");
            }
        }
        return "{\n  \"multipart\": [\n" + String.join(",\n", parts) + "\n  ]\n}\n";
    }

    private static String body() {
        String slab = element(0, 0, FRONT, 16, 16, 16,
                face("south", 0, 8, 8, 16, ", \"cullface\": \"south\""), face("east", 11.25, 0, 12.75, 8, ""),
                face("west", 11.25, 0, 12.75, 8, ""), face("up", 8, 3.25, 16, 4.75, ""), face("down", 8, 3.25, 16, 4.75, ""));
        return model(slab, front(MIDDLE, MIDDLE, MIDDLE, MIDDLE));
    }

    private static String piece(Span columns, Span rows, Span sourceColumns, Span sourceRows) {
        return model(front(columns, rows, sourceColumns, sourceRows));
    }

    private static String front(Span columns, Span rows, Span sourceColumns, Span sourceRows) {
        return element(16 - columns.to() / TEXELS_PER_UNIT, 16 - rows.to() / TEXELS_PER_UNIT, FRONT,
                16 - columns.from() / TEXELS_PER_UNIT, 16 - rows.from() / TEXELS_PER_UNIT, FRONT,
                face("north", sourceColumns.from() / TEXELS_PER_UV, sourceRows.from() / TEXELS_PER_UV,
                        sourceColumns.to() / TEXELS_PER_UV, sourceRows.to() / TEXELS_PER_UV, ""));
    }

    private static String model(String... elements) {
        return "{\n  \"parent\": \"minecraft:block/block\",\n  \"texture_size\": [64, 64],\n  \"textures\": {\"0\": \"" + TEXTURE
                + "\", \"particle\": \"" + TEXTURE + "\"},\n  \"elements\": [\n" + String.join(",\n", elements) + "\n  ]\n}\n";
    }

    private static String element(double x0, double y0, double z0, double x1, double y1, double z1, String... faces) {
        return "    {\"from\": [" + number(x0) + ", " + number(y0) + ", " + number(z0) + "], \"to\": [" + number(x1) + ", " + number(y1) + ", "
                + number(z1) + "], \"faces\": {" + String.join(", ", faces) + "}}";
    }

    private static String face(String name, double u0, double v0, double u1, double v1, String extra) {
        return "\"" + name + "\": {\"uv\": [" + number(u0) + ", " + number(v0) + ", " + number(u1) + ", " + number(v1) + "], \"texture\": \"#0\""
                + extra + "}";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%s", value);
    }

    private static void write(String name, String json) throws IOException {
        Files.writeString(ASSETS.resolve("models/block/" + name + ".json"), json);
    }
}
