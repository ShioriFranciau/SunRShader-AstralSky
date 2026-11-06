import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Converts a source image to PNG for Iris custom-texture compatibility. */
public final class ImageFormatConverter {
    private ImageFormatConverter() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: ImageFormatConverter <input> <output.png>");
        }
        BufferedImage image = ImageIO.read(Path.of(arguments[0]).toFile());
        if (image == null) {
            throw new IllegalArgumentException("Unsupported source image: " + arguments[0]);
        }
        if (!ImageIO.write(image, "png", Path.of(arguments[1]).toFile())) {
            throw new IllegalStateException("No PNG writer is available");
        }
        System.out.printf("Converted %s to %s (%dx%d)%n",
            arguments[0], arguments[1], image.getWidth(), image.getHeight());
    }
}
