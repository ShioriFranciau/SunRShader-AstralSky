import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Adds a sparse point-source mask to alpha without changing an atlas' RGB data. */
public final class StarAtlasPointEncoder {
    private StarAtlasPointEncoder() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("Usage: StarAtlasPointEncoder <atlas.png>...");
        }
        for (String argument : arguments) {
            encode(Path.of(argument));
        }
    }

    private static void encode(Path path) throws Exception {
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) {
            throw new IllegalArgumentException("Not an image: " + path);
        }
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage encoded = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int sources = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y) & 0x00ffffff;
                int peak = intensity(rgb);
                boolean isSource = peak > 0;
                for (int dy = -1; isSource && dy <= 1; dy++) {
                    int neighborY = Math.clamp(y + dy, 0, height - 1);
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int neighborX = Math.floorMod(x + dx, width);
                        int neighbor = intensity(source.getRGB(neighborX, neighborY));
                        // Break plateaus in a stable direction so each bright
                        // region receives one point-source marker.
                        if (neighbor > peak || (neighbor == peak && (dy < 0 || (dy == 0 && dx < 0)))) {
                            isSource = false;
                            break;
                        }
                    }
                }
                if (isSource) {
                    sources++;
                }
                encoded.setRGB(x, y, (isSource ? 0xff000000 : 0) | rgb);
            }
        }

        ImageIO.write(encoded, "png", path.toFile());
        System.out.printf("Encoded %,d point sources in %s (%dx%d)%n", sources, path, width, height);
    }

    private static int intensity(int color) {
        return Math.max((color >>> 16) & 255, Math.max((color >>> 8) & 255, color & 255));
    }
}
