import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts HEASARC BSC5P or Hipparcos VOTable responses into an equirectangular RGB star atlas. */
public final class StarAtlasGenerator {
    private static int width = 4096;
    private static int height = 2048;
    private static final double CATALOG_LIMIT = 12.5;
    private static final Pattern TABLE_ROW = Pattern.compile("<TR>(.*?)</TR>", Pattern.DOTALL);
    private static final Pattern TABLE_CELL = Pattern.compile("<TD>(.*?)</TD>", Pattern.DOTALL);

    private StarAtlasGenerator() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2 || arguments.length > 3) {
            throw new IllegalArgumentException(
                "Usage: StarAtlasGenerator <catalogue.xml> <atlas.png> [bsc5|hipparcos|tycho2]"
            );
        }

        Path source = Path.of(arguments[0]);
        Path destination = Path.of(arguments[1]);
        AtlasProfile profile = AtlasProfile.fromToken(
            arguments.length == 3 ? arguments[2] : "bsc5"
        );
        CatalogueFormat format = profile.format();
        width = profile.width();
        height = profile.height();
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        List<Path> sources;
        if (Files.isDirectory(source)) {
            try (var files = Files.list(source)) {
                sources = files
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
            }
        } else {
            sources = List.of(source);
        }
        int records = 0;
        int rendered = 0;
        for (Path part : sources) {
            Counts partCounts = renderSource(part, atlas, format);
            records += partCounts.records;
            rendered += partCounts.rendered;
            System.out.printf("Read %,d records from %s%n", partCounts.records, part);
        }
        Counts counts = new Counts(records, rendered);

        int minimumRecords = format == CatalogueFormat.TYCHO2 ? 2_500_000 : 9_000;
        int minimumRendered = format == CatalogueFormat.TYCHO2 ? 2_400_000 : 8_000;
        if (counts.records < minimumRecords || counts.rendered < minimumRendered) {
            throw new IllegalStateException("Catalogue response appears incomplete: "
                + counts.records + " records, " + counts.rendered + " rendered");
        }

        if (format == CatalogueFormat.TYCHO2) {
            mergeReferenceAtlas(
                atlas,
                destination.resolveSibling("hipparcos-stars.png")
            );
        }

        Files.createDirectories(destination.toAbsolutePath().normalize().getParent());
        ImageIO.write(atlas, "png", destination.toFile());
        System.out.printf("Rendered %d of %d catalogue records to %s (%dx%d)%n",
            counts.rendered, counts.records, destination, width, height);
    }

    private static Counts renderTableData(String xml, BufferedImage atlas) {
        Matcher rows = TABLE_ROW.matcher(xml);
        int records = 0;
        int rendered = 0;
        while (rows.find()) {
            Matcher cells = TABLE_CELL.matcher(rows.group(1));
            String[] values = new String[5];
            int count = 0;
            while (cells.find() && count < values.length) {
                values[count++] = cells.group(1).trim();
            }
            // HEASARC adds w3___row before requested ra, dec, vmag and B-V.
            if (count < 5) {
                continue;
            }
            records++;
            try {
                double rightAscension = Double.parseDouble(values[1]);
                double declination = Double.parseDouble(values[2]);
                double visualMagnitude = Double.parseDouble(values[3]);
                double bvColour = values[4].isEmpty() ? 0.4 : Double.parseDouble(values[4]);
                if (Double.isFinite(rightAscension) && Double.isFinite(declination)
                    && Double.isFinite(visualMagnitude) && visualMagnitude <= CATALOG_LIMIT) {
                    splatStar(atlas, rightAscension, declination, visualMagnitude, bvColour);
                    rendered++;
                }
            } catch (NumberFormatException ignored) {
                // Hipparcos contains a small number of rows without photometry.
            }
        }
        return new Counts(records, rendered);
    }

    private static Counts renderSource(
        Path source,
        BufferedImage atlas,
        CatalogueFormat format
    ) throws Exception {
        String xml = Files.readString(source);
        return xml.contains("<TABLEDATA>")
            ? renderTableData(xml, atlas)
            : renderBinary(source, atlas, format);
    }

    private static Counts renderBinary(
        Path source,
        BufferedImage atlas,
        CatalogueFormat format
    ) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(source.toFile());
        rejectIncompleteQuery(document);

        var streams = document.getElementsByTagNameNS("*", "STREAM");
        if (streams.getLength() != 1) {
            throw new IllegalArgumentException("Expected exactly one VOTable STREAM element");
        }
        byte[] binary = Base64.getMimeDecoder().decode(streams.item(0).getTextContent());

        // Deserialize each row according to the response's own FIELD
        // declarations: catalogue tables differ in both column order and
        // numeric width (Hipparcos magnitudes are doubles, BSC5P floats), so
        // a fixed layout cannot be assumed.
        List<BinaryField> fields = readBinaryFields(document);
        int records = 0;
        int rendered = 0;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(binary))) {
            while (input.available() > 0) {
                double rightAscension = Double.NaN;
                double declination = Double.NaN;
                double visualMagnitude = Double.NaN;
                double bvColour = Double.NaN;
                double btMagnitude = Double.NaN;
                double vtMagnitude = Double.NaN;
                for (BinaryField field : fields) {
                    if ("char".equals(field.datatype())) {
                        int length = field.variableLength()
                            ? input.readInt()
                            : field.arraysize();
                        input.skipNBytes(length);
                        continue;
                    }
                    double value = switch (field.datatype()) {
                        case "double" -> input.readDouble();
                        case "float" -> input.readFloat();
                        case "int" -> input.readInt();
                        case "short" -> input.readShort();
                        case "long" -> input.readLong();
                        case "boolean" -> input.readUnsignedByte();
                        default -> throw new IllegalArgumentException(
                            "Unsupported VOTable binary datatype: " + field.datatype()
                        );
                    };
                    switch (field.name()) {
                        case "ra", "mean_ra", "observed_ra" -> rightAscension = value;
                        case "dec", "mean_dec", "observed_dec" -> declination = value;
                        case "vmag" -> visualMagnitude = value;
                        case "bv_color" -> bvColour = value;
                        case "bt_mag" -> btMagnitude = value;
                        case "vt_mag" -> vtMagnitude = value;
                        default -> {
                        }
                    }
                }

                double bv;
                if (format == CatalogueFormat.TYCHO2) {
                    if (Double.isFinite(btMagnitude) && Double.isFinite(vtMagnitude)) {
                        double tychoColour = btMagnitude - vtMagnitude;
                        visualMagnitude = vtMagnitude - 0.090 * tychoColour;
                        bv = 0.850 * tychoColour;
                    } else if (Double.isFinite(vtMagnitude)) {
                        visualMagnitude = vtMagnitude;
                        bv = 0.4;
                    } else {
                        // A small number of records only have BT. Adopt a
                        // neutral stellar colour to estimate Johnson V.
                        visualMagnitude = btMagnitude - 0.51;
                        bv = 0.4;
                    }
                } else {
                    if (!Double.isFinite(bvColour)) {
                        bvColour = 0.4;
                    }
                    bv = bvColour;
                }
                records++;
                if (Double.isFinite(rightAscension) && Double.isFinite(declination)
                    && Double.isFinite(visualMagnitude) && visualMagnitude <= CATALOG_LIMIT) {
                    splatStar(atlas, rightAscension, declination, visualMagnitude, bv);
                    rendered++;
                }
            }
        }
        return new Counts(records, rendered);
    }

    private static void rejectIncompleteQuery(Document document) {
        var info = document.getElementsByTagNameNS("*", "INFO");
        for (int index = 0; index < info.getLength(); index++) {
            var element = (org.w3c.dom.Element) info.item(index);
            String status = element.getAttribute("value");
            if ("QUERY_STATUS".equals(element.getAttribute("name")) && !"OK".equals(status)) {
                // A value-less INFO carries the status in its text content,
                // and XMin reports truncated sync results as OVERFLOW.
                String detail = status.isEmpty()
                    ? element.getTextContent().trim()
                    : status;
                throw new IllegalArgumentException("HEASARC query did not complete: " + detail);
            }
        }
    }

    private static List<BinaryField> readBinaryFields(Document document) {
        var elements = document.getElementsByTagNameNS("*", "FIELD");
        List<BinaryField> fields = new ArrayList<>(elements.getLength());
        for (int index = 0; index < elements.getLength(); index++) {
            var element = (org.w3c.dom.Element) elements.item(index);
            String arraysize = element.getAttribute("arraysize");
            boolean variableLength = arraysize.contains("*");
            fields.add(new BinaryField(
                element.getAttribute("name"),
                element.getAttribute("datatype"),
                variableLength || arraysize.isEmpty()
                    ? 0
                    : Integer.parseInt(arraysize),
                variableLength
            ));
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("VOTable response declares no FIELD elements");
        }
        return fields;
    }

    private record BinaryField(
        String name,
        String datatype,
        int arraysize,
        boolean variableLength
    ) {
    }

    private static void splatStar(
        BufferedImage image,
        double rightAscension,
        double declination,
        double visualMagnitude,
        double bvColour
    ) {
        int centerX = floorMod((int) Math.round(rightAscension / 360.0 * width), width);
        int centerY = clamp(
            (int) Math.round((declination + 90.0) / 180.0 * (height - 1)),
            0,
            height - 1
        );

        double encodedIntensity = clamp(
            Math.pow(2.512, 4.5 - visualMagnitude) * 0.060,
            1.0 / 255.0,
            1.0
        );
        double[] colour = colourFromBv(Double.isFinite(bvColour) ? bvColour : 0.4);

        for (int offsetY = -4; offsetY <= 4; offsetY++) {
            int y = clamp(centerY + offsetY, 0, height - 1);
            for (int offsetX = -4; offsetX <= 4; offsetX++) {
                int x = floorMod(centerX + offsetX, width);
                double distanceSquared = offsetX * offsetX + offsetY * offsetY;
                double kernel = Math.exp(-distanceSquared / 4.5);
                addPixel(image, x, y, colour, encodedIntensity * kernel);
            }
        }

        // RGB keeps the softly filtered atlas used at normal FOV. Alpha is a
        // sparse point-source mask which the shader uses when atlas texels are
        // magnified at a narrow FOV.
        int center = image.getRGB(centerX, centerY);
        image.setRGB(centerX, centerY, 0xff000000 | (center & 0x00ffffff));
    }

    private static void addPixel(
        BufferedImage image,
        int x,
        int y,
        double[] colour,
        double intensity
    ) {
        int current = image.getRGB(x, y);
        int red = clamp((current >>> 16 & 0xff) + (int) Math.round(colour[0] * intensity * 255.0), 0, 255);
        int green = clamp((current >>> 8 & 0xff) + (int) Math.round(colour[1] * intensity * 255.0), 0, 255);
        int blue = clamp((current & 0xff) + (int) Math.round(colour[2] * intensity * 255.0), 0, 255);
        image.setRGB(x, y, (current & 0xff000000) | red << 16 | green << 8 | blue);
    }

    private static void mergeReferenceAtlas(
        BufferedImage destination,
        Path referencePath
    ) throws Exception {
        BufferedImage reference = ImageIO.read(referencePath.toFile());
        if (reference == null
            || destination.getWidth() != reference.getWidth() * 2
            || destination.getHeight() != reference.getHeight() * 2) {
            throw new IllegalStateException(
                "Tycho-2 bright-star reference must be a half-resolution atlas: "
                    + referencePath
            );
        }
        for (int y = 0; y < reference.getHeight(); y++) {
            for (int x = 0; x < reference.getWidth(); x++) {
                int referencePixel = reference.getRGB(x, y);
                int rgb = referencePixel & 0x00ffffff;
                if (rgb == 0) {
                    continue;
                }
                int targetX = x * 2;
                int targetY = y * 2;
                mergeMaximum(destination, targetX, targetY, rgb);
                mergeMaximum(destination, targetX + 1, targetY, rgb);
                mergeMaximum(destination, targetX, targetY + 1, rgb);
                mergeMaximum(destination, targetX + 1, targetY + 1, rgb);
                if ((referencePixel >>> 24) > 127) {
                    destination.setRGB(
                        targetX,
                        targetY,
                        0xff000000 | (destination.getRGB(targetX, targetY) & 0x00ffffff)
                    );
                }
            }
        }
    }

    private static void mergeMaximum(
        BufferedImage image,
        int x,
        int y,
        int rgb
    ) {
        int current = image.getRGB(x, y);
        int red = Math.max((current >>> 16) & 255, (rgb >>> 16) & 255);
        int green = Math.max((current >>> 8) & 255, (rgb >>> 8) & 255);
        int blue = Math.max(current & 255, rgb & 255);
        image.setRGB(
            x,
            y,
            (current & 0xff000000) | red << 16 | green << 8 | blue
        );
    }

    private static double[] colourFromBv(double bv) {
        double value = clamp(bv, -0.35, 1.90);
        if (value < 0.40) {
            return mix(new double[] {0.62, 0.74, 1.00}, new double[] {1.00, 0.98, 0.93},
                (value + 0.35) / 0.75);
        }
        return mix(new double[] {1.00, 0.98, 0.93}, new double[] {1.00, 0.56, 0.30},
            (value - 0.40) / 1.50);
    }

    private static double[] mix(double[] from, double[] to, double amount) {
        return new double[] {
            from[0] + (to[0] - from[0]) * amount,
            from[1] + (to[1] - from[1]) * amount,
            from[2] + (to[2] - from[2]) * amount
        };
    }

    private static int floorMod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Counts(int records, int rendered) {
    }

    private enum CatalogueFormat {
        STANDARD,
        TYCHO2
    }

    /**
     * Per-catalogue atlas profile. The dimensions in this one table are the
     * single source of truth for every generated atlas; tycho2 must stay at
     * exactly twice the hipparcos dimensions in both axes because the render
     * merges the hipparcos bright-star atlas into it.
     */
    private enum AtlasProfile {
        BSC5("bsc5", CatalogueFormat.STANDARD, 4096, 2048),
        HIPPARCOS("hipparcos", CatalogueFormat.STANDARD, 8192, 4096),
        TYCHO2("tycho2", CatalogueFormat.TYCHO2, 16384, 8192);

        private final String token;
        private final CatalogueFormat format;
        private final int width;
        private final int height;

        AtlasProfile(
            String token,
            CatalogueFormat format,
            int width,
            int height
        ) {
            this.token = token;
            this.format = format;
            this.width = width;
            this.height = height;
        }

        static AtlasProfile fromToken(String token) {
            for (AtlasProfile candidate : values()) {
                if (candidate.token.equalsIgnoreCase(token)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException(
                "Unknown catalogue profile '" + token + "': expected bsc5, hipparcos or tycho2"
            );
        }

        CatalogueFormat format() {
            return format;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }
    }
}
