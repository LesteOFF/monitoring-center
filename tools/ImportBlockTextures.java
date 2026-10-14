import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

/**
 * Imports the Central and Sensor atlases, preserving the approved screen texture byte-for-byte.
 * Run from the project root with Java 21: java tools/ImportBlockTextures.java
 * Optional arguments: source directory, texture directory, preview directory, Create jar.
 * Each input is a square, opaque, 2 x 2 atlas. Cells become exactly 16 x 16 pixels
 * using nearest-neighbour sampling; no blur, transparency, or cross-cell sampling.
 * Models keep Minecraft UV coordinates in the 0..16 range: each cell spans 8 units.
 * The coupler references native Create materials. The preview resolves those from its jar.
 * The software preview checks real model UVs; it is not an in-game screenshot.
 */
public final class ImportBlockTextures {
    private static final String[] NAMES = {"central", "sensor", "kinetic_coupler", "screen"};
    private static final String[] LABELS = {"Central de monitoramento", "Sensor", "Acoplador cinetico", "Tela modular"};
    private static final int CELL = 16;
    private static final int SIZE = CELL * 2;
    private static final Path MODEL_DIRECTORY = Path.of("src/main/resources/assets/monitoringcenter/models/block");

    public static void main(String[] args) throws IOException {
        if (args.length > 4) {
            throw new IllegalArgumentException("Expected up to four paths: source, textures, previews, Create jar");
        }
        Path source = args.length > 0 ? Path.of(args[0]) : Path.of("output/art-source-v2");
        Path textures = args.length > 1 ? Path.of(args[1]) : Path.of("src/main/resources/assets/monitoringcenter/textures/block");
        Path previews = args.length > 2 ? Path.of(args[2]) : Path.of("output/texture-review");
        Path createJar = args.length > 3 ? Path.of(args[3]) : findCreateJar();
        List<BufferedImage> atlases = new ArrayList<>();
        List<List<Element>> models = new ArrayList<>();
        // Validate both new inputs before replacing any production texture.
        Map<String, BufferedImage> resources = new LinkedHashMap<>();
        for (String name : List.of("central", "sensor")) {
            BufferedImage atlas = importAtlas(source.resolve(name + ".png"));
            atlases.add(atlas);
            resources.put("monitoringcenter:block/" + name, atlas);
        }
        BufferedImage screen = readImage(textures.resolve("screen.png"));
        resources.put("monitoringcenter:block/screen", screen);
        try (ZipFile jar = new ZipFile(createJar.toFile())) {
            for (String name : List.of("andesite_casing", "gearbox", "axis", "axis_top")) {
                String entry = "assets/create/textures/block/" + name + ".png";
                if (jar.getEntry(entry) == null) throw new IOException("Create resource is missing: " + entry);
                try (var input = jar.getInputStream(jar.getEntry(entry))) {
                    BufferedImage texture = ImageIO.read(input);
                    if (texture == null) throw new IOException("Invalid Create texture: " + entry);
                    resources.put("create:block/" + name, texture);
                }
            }
        }
        // Use the item model for a static preview of the animated input shaft.
        for (String name : NAMES) {
            String model = name.equals("kinetic_coupler") ? "kinetic_coupler_item" : name;
            models.add(readModel(MODEL_DIRECTORY.resolve(model + ".json"), resources));
        }
        readModel(MODEL_DIRECTORY.resolve("kinetic_coupler.json"), resources);
        atlases.add(createMaterials(resources));
        atlases.add(screen);
        Files.createDirectories(textures);
        Files.createDirectories(previews);
        for (int i = 0; i < 2; i++) {
            write(atlases.get(i), textures.resolve(NAMES[i] + ".png"));
            System.out.println(NAMES[i] + ": 32x32, " + models.get(i).size() + " elements, UVs validated");
        }
        System.out.println("kinetic_coupler: native Create materials, animated shaft; screen: preserved");
        write(atlasBoard(atlases), previews.resolve("texture-atlases.png"));
        write(modelBoard(atlases, models), previews.resolve("block-models.png"));
        System.out.println("Preview images: " + previews.toAbsolutePath());
    }

    private static Path findCreateJar() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) { properties.load(reader); }
        Path mods = Path.of(properties.getProperty("pack_mods_dir"));
        try (var files = Files.list(mods)) {
            return files.filter(path -> path.getFileName().toString().matches("create-.*\\.jar"))
                    .findFirst().orElseThrow(() -> new IOException("Create jar not found in " + mods));
        }
    }

    private static BufferedImage readImage(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Invalid PNG: " + file);
        return image;
    }

    private static BufferedImage createMaterials(Map<String, BufferedImage> resources) {
        BufferedImage materials = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = materials.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] names = {"andesite_casing", "gearbox", "axis", "axis_top"};
        for (int i = 0; i < names.length; i++) {
            g.drawImage(resources.get("create:block/" + names[i]), i % 2 * CELL, i / 2 * CELL, CELL, CELL, null);
        }
        g.dispose();
        return materials;
    }

    private static BufferedImage importAtlas(Path file) throws IOException {
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null || source.getWidth() != source.getHeight()
                || source.getWidth() < SIZE || source.getWidth() % 2 != 0) {
            throw new IOException(file + ": expected a square PNG with an even size of at least 32 pixels");
        }
        BufferedImage atlas = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        int sourceCell = source.getWidth() / 2;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sx = x / CELL * sourceCell + (int) ((x % CELL + 0.5) * sourceCell / CELL);
                int sy = y / CELL * sourceCell + (int) ((y % CELL + 0.5) * sourceCell / CELL);
                int color = source.getRGB(sx, sy);
                if ((color >>> 24) != 255) {
                    throw new IOException(file + ": block atlas must be opaque (sampled pixel " + sx + "," + sy + ")");
                }
                atlas.setRGB(x, y, color);
            }
        }
        return atlas;
    }

    @SuppressWarnings("unchecked")
    private static List<Element> readModel(Path file, Map<String, BufferedImage> resources) throws IOException {
        Map<String, Object> model = (Map<String, Object>) new Json(Files.readString(file, StandardCharsets.UTF_8)).read();
        Map<String, Object> textures = (Map<String, Object>) model.get("textures");
        List<Element> elements = new ArrayList<>();
        for (Object entry : (List<Object>) model.get("elements")) {
            Map<String, Object> element = (Map<String, Object>) entry;
            double[] from = numbers(element.get("from"));
            double[] to = numbers(element.get("to"));
            for (int axis = 0; axis < 3; axis++) {
                if (to[axis] <= from[axis] || from[axis] < 0 || to[axis] > 16) {
                    throw new IOException(file + ": invalid geometry or outside the block bounds");
                }
            }
            Map<String, Face> faces = new LinkedHashMap<>();
            for (Map.Entry<String, Object> faceEntry : ((Map<String, Object>) element.get("faces")).entrySet()) {
                Map<String, Object> face = (Map<String, Object>) faceEntry.getValue();
                double[] uv = numbers(face.get("uv"));
                if (uv.length != 4 || Arrays.stream(uv).anyMatch(value -> value < 0 || value > 16)
                        || uv[0] == uv[2] || uv[1] == uv[3]) {
                    throw new IOException(file + ": invalid UVs on " + element.get("name") + "/" + faceEntry.getKey());
                }
                String texture = (String) face.get("texture");
                for (int links = 0; texture.startsWith("#") && links < 16; links++) {
                    texture = (String) textures.get(texture.substring(1));
                    if (texture == null) throw new IOException(file + ": missing texture reference");
                }
                BufferedImage image = resources.get(texture);
                if (image == null) throw new IOException(file + ": unresolved texture " + texture);
                faces.put(faceEntry.getKey(), new Face(uv, ((Number) face.getOrDefault("rotation", 0)).intValue(), image));
            }
            if (element.containsKey("rotation")) {
                Map<String, Object> rotation = (Map<String, Object>) element.get("rotation");
                if (((Number) rotation.get("angle")).doubleValue() != 0) {
                    throw new IOException(file + ": software preview does not support rotated elements");
                }
            }
            elements.add(new Element(from, to, faces));
        }
        return elements;
    }

    private static double[] numbers(Object values) {
        return ((List<?>) values).stream().mapToDouble(value -> ((Number) value).doubleValue()).toArray();
    }

    private static BufferedImage atlasBoard(List<BufferedImage> atlases) {
        BufferedImage image = new BufferedImage(1640, 526, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(22, 28, 36));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(229, 239, 244));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("Central / Sensor: 16 px por face | Acoplador: materiais Create | Tela preservada", 24, 35);
        for (int i = 0; i < atlases.size(); i++) {
            int x = 24 + i * 405;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(LABELS[i], x, 72);
            g.drawImage(atlases.get(i), x, 86, 384, 384, null);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString("Superior: frente | carcaça. Inferior: ventilação | conector/detalhe.", 24, 508);
        g.dispose();
        return image;
    }

    private static BufferedImage modelBoard(List<BufferedImage> atlases, List<List<Element>> models) {
        int width = 1360;
        int height = 820;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(22, 28, 36));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(229, 239, 244));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("Modelos com as UVs finais", 24, 34);
        for (int i = 0; i < NAMES.length; i++) {
            int x = 10 + i * 340;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(LABELS[i], x + 14, 72);
            boolean frontIsSouth = i == 1 || i == 2;
            g.drawImage(render(models.get(i), frontIsSouth ? 1 : -1), x, 88, null);
            g.drawImage(render(models.get(i), frontIsSouth ? -1 : 1), x, 448, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            g.setColor(new Color(154, 177, 186));
            g.drawString("Frente", x + 14, 427);
            g.drawString("Verso", x + 14, 785);
            g.setColor(new Color(229, 239, 244));
        }
        g.setColor(new Color(154, 177, 186));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("Previa por software. Iluminaçao aproximada; a tela mostra apenas a base, sem o conteudo dinamico do jogo.", 24, 810);
        g.dispose();
        return image;
    }

    private static BufferedImage render(List<Element> elements, int viewZ) {
        int width = 330;
        int height = 330;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 38, 47));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(19, 24, 29));
        g.fillOval(59, 265, 230, 43);
        g.dispose();
        double[] depth = new double[width * height];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (Element element : elements) {
            for (String side : List.of("up", "east", viewZ < 0 ? "north" : "south")) {
                Face face = element.faces().get(side);
                if (face == null) continue;
                double[][] vertices = vertices(element, side);
                Vertex[] projected = new Vertex[4];
                double[] uv = face.uv();
                double[][] coords = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
                for (int v = 0; v < 4; v++) {
                    double px = vertices[v][0] - 8;
                    double py = vertices[v][1] - 8;
                    double pz = vertices[v][2] - 8;
                    double screenX = px * viewZ * .7071 - pz * .7071;
                    double screenY = .32 * px - .845 * py + .32 * pz * viewZ;
                    double screenZ = .60 * px + .453 * py + .60 * pz * viewZ;
                    int corner = Math.floorMod(v - face.rotation() / 90, 4);
                    projected[v] = new Vertex(165 + screenX * 11, 172 + screenY * 11,
                            screenZ, coords[corner][0] * face.image().getWidth() / 16.0,
                            coords[corner][1] * face.image().getHeight() / 16.0);
                }
                double shade = side.equals("up") ? 1.13 : side.equals("east") ? .78 : 1.0;
                triangle(image, face.image(), depth, projected[0], projected[1], projected[2], shade);
                triangle(image, face.image(), depth, projected[0], projected[2], projected[3], shade);
            }
        }
        return image;
    }

    private static double[][] vertices(Element e, String side) {
        double x0 = e.from()[0], y0 = e.from()[1], z0 = e.from()[2];
        double x1 = e.to()[0], y1 = e.to()[1], z1 = e.to()[2];
        return switch (side) {
            case "north" -> new double[][] {{x1,y1,z0},{x0,y1,z0},{x0,y0,z0},{x1,y0,z0}};
            case "south" -> new double[][] {{x0,y1,z1},{x1,y1,z1},{x1,y0,z1},{x0,y0,z1}};
            case "east" -> new double[][] {{x1,y1,z1},{x1,y1,z0},{x1,y0,z0},{x1,y0,z1}};
            case "up" -> new double[][] {{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}};
            default -> throw new IllegalArgumentException("Unexpected preview face " + side);
        };
    }

    private static void triangle(BufferedImage output, BufferedImage atlas, double[] depth,
                                 Vertex a, Vertex b, Vertex c, double shade) {
        int minX = Math.max(0, (int) Math.floor(Math.min(a.x(), Math.min(b.x(), c.x()))));
        int maxX = Math.min(output.getWidth() - 1, (int) Math.ceil(Math.max(a.x(), Math.max(b.x(), c.x()))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a.y(), Math.min(b.y(), c.y()))));
        int maxY = Math.min(output.getHeight() - 1, (int) Math.ceil(Math.max(a.y(), Math.max(b.y(), c.y()))));
        double area = edge(a, b, c.x(), c.y());
        if (Math.abs(area) < .0001) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double wa = edge(b, c, x + .5, y + .5) / area;
                double wb = edge(c, a, x + .5, y + .5) / area;
                double wc = 1 - wa - wb;
                if (wa < -1e-6 || wb < -1e-6 || wc < -1e-6) continue;
                double z = wa * a.z() + wb * b.z() + wc * c.z();
                int pixel = y * output.getWidth() + x;
                if (z < depth[pixel]) continue;
                int u = clamp((int) Math.floor(wa * a.u() + wb * b.u() + wc * c.u()), 0, atlas.getWidth() - 1);
                int v = clamp((int) Math.floor(wa * a.v() + wb * b.v() + wc * c.v()), 0, atlas.getHeight() - 1);
                int color = atlas.getRGB(u, v);
                int r = clamp((int) ((color >> 16 & 255) * shade), 0, 255);
                int gr = clamp((int) ((color >> 8 & 255) * shade), 0, 255);
                int bl = clamp((int) ((color & 255) * shade), 0, 255);
                output.setRGB(x, y, r << 16 | gr << 8 | bl);
                depth[pixel] = z;
            }
        }
    }

    private static double edge(Vertex a, Vertex b, double x, double y) {
        return (x - a.x()) * (b.y() - a.y()) - (y - a.y()) * (b.x() - a.x());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void write(BufferedImage image, Path file) throws IOException {
        if (!ImageIO.write(image, "png", file.toFile())) throw new IOException("No PNG writer available");
    }

    private record Element(double[] from, double[] to, Map<String, Face> faces) {}
    private record Face(double[] uv, int rotation, BufferedImage image) {}
    private record Vertex(double x, double y, double z, double u, double v) {}

    /** Small JSON reader so this command also works outside Gradle without Gson. */
    private static final class Json {
        private final String text;
        private int pos;

        Json(String text) { this.text = text; }

        Object read() {
            Object result = value();
            whitespace();
            if (pos != text.length()) throw error("trailing data");
            return result;
        }

        private Object value() {
            whitespace();
            if (pos == text.length()) throw error("unexpected end");
            char current = text.charAt(pos);
            if (current == '{') return object();
            if (current == '[') return array();
            if (current == '"') return string();
            for (String literal : List.of("true", "false", "null")) {
                if (text.startsWith(literal, pos)) {
                    pos += literal.length();
                    return literal.equals("null") ? null : literal.equals("true");
                }
            }
            int start = pos;
            while (pos < text.length() && "-+.0123456789eE".indexOf(text.charAt(pos)) >= 0) pos++;
            if (pos == start) throw error("expected value");
            return Double.parseDouble(text.substring(start, pos));
        }

        private Map<String, Object> object() {
            pos++;
            Map<String, Object> values = new LinkedHashMap<>();
            whitespace();
            if (consume('}')) return values;
            do {
                whitespace();
                String key = string();
                whitespace();
                require(':');
                values.put(key, value());
                whitespace();
            } while (consume(','));
            require('}');
            return values;
        }

        private List<Object> array() {
            pos++;
            List<Object> values = new ArrayList<>();
            whitespace();
            if (consume(']')) return values;
            do {
                values.add(value());
                whitespace();
            } while (consume(','));
            require(']');
            return values;
        }

        private String string() {
            require('"');
            StringBuilder value = new StringBuilder();
            while (pos < text.length()) {
                char current = text.charAt(pos++);
                if (current == '"') return value.toString();
                if (current == '\\') {
                    char escape = text.charAt(pos++);
                    if (escape == 'u') {
                        value.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    } else {
                        value.append(switch (escape) {
                            case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                            case 'b' -> '\b'; case 'f' -> '\f';
                            case '"', '/', '\\' -> escape;
                            default -> throw error("invalid escape");
                        });
                    }
                } else value.append(current);
            }
            throw error("unclosed string");
        }

        private void whitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        private boolean consume(char expected) {
            if (pos < text.length() && text.charAt(pos) == expected) { pos++; return true; }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) throw error("expected " + expected);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at character " + pos + ": " + message);
        }
    }
}
