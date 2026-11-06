package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarAtlasTest {
    @Test
    void bothAtlasesContainKnownStarsAcrossTheSkyAndRaSeam() throws Exception {
        for (String file : new String[] {
            "bsc5-stars.png", "hipparcos-stars.png", "tycho2-stars.png"
        }) {
            BufferedImage atlas = ImageIO.read(Path.of(
                "shaderpack/SunRShader-AstralSky/shaders/textures/" + file
            ).toFile());
            assertNotNull(atlas, file);
            // Independent J2000 catalogue coordinates spanning both hemispheres.
            assertTrue(peak(atlas, 79.1723, 45.9979) > 40, file + " Capella");
            assertTrue(peak(atlas, 101.2872, -16.7161) > 40, file + " Sirius");
            assertTrue(peak(atlas, 37.9546, 89.2641) > 8, file + " Polaris");
            assertTrue(peak(atlas, 95.9879, -52.6957) > 20, file + " Canopus");
            assertTrue(peak(atlas, 213.9153, 19.1824) > 20, file + " Arcturus");
            assertTrue(peak(atlas, 279.2347, 38.7837) > 20, file + " Vega");
            assertTrue(peak(atlas, 24.4286, -57.2368) > 10, file + " Achernar");
            assertTrue(peak(atlas, 186.6496, -63.0991) > 10, file + " Acrux");
        }
    }

    @Test
    void expandedAtlasHasMateriallyMoreOccupiedPixels() throws Exception {
        BufferedImage bsc = ImageIO.read(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/textures/bsc5-stars.png"
        ).toFile());
        BufferedImage hipparcos = ImageIO.read(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/textures/hipparcos-stars.png"
        ).toFile());
        BufferedImage tycho2 = ImageIO.read(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/textures/tycho2-stars.png"
        ).toFile());
        assertTrue(occupiedPixels(hipparcos) > occupiedPixels(bsc) * 3L);
        assertTrue(occupiedPixels(tycho2) > occupiedPixels(hipparcos) * 3L);
        // The generator merges the hipparcos bright-star atlas into tycho2,
        // which requires tycho2 to stay at exactly twice the hipparcos
        // dimensions in both axes, and the expanded atlas at 16K width.
        assertTrue(tycho2.getWidth() == hipparcos.getWidth() * 2);
        assertTrue(tycho2.getHeight() == hipparcos.getHeight() * 2);
        assertTrue(tycho2.getWidth() >= 8192);
    }

    @Test
    void atlasesContainSparsePointSourceMasksForNarrowFovRendering() throws Exception {
        for (String file : new String[] {
            "bsc5-stars.png", "hipparcos-stars.png", "tycho2-stars.png"
        }) {
            BufferedImage atlas = ImageIO.read(Path.of(
                "shaderpack/SunRShader-AstralSky/shaders/textures/" + file
            ).toFile());
            long sources = alphaSources(atlas);
            assertTrue(sources > 1_000, file + " point sources");
            assertTrue(sources < occupiedPixels(atlas) / 4, file + " sparse alpha mask");
            if (file.startsWith("tycho2")) {
                assertTrue(sources > 1_000_000, file + " expanded point-source count");
            }
        }
    }

    private static long occupiedPixels(BufferedImage image) {
        long occupied = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0) {
                    occupied++;
                }
            }
        }
        return occupied;
    }

    private static long alphaSources(BufferedImage image) {
        long sources = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 127) {
                    sources++;
                }
            }
        }
        return sources;
    }

    private static int peak(BufferedImage image, double ra, double dec) {
        int x = (int) Math.round(ra / 360.0 * image.getWidth());
        int y = (int) Math.round((dec + 90.0) / 180.0 * (image.getHeight() - 1));
        int peak = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int px = Math.floorMod(x + dx, image.getWidth());
                int py = Math.clamp(y + dy, 0, image.getHeight() - 1);
                int color = image.getRGB(px, py);
                peak = Math.max(peak, Math.max((color >>> 16) & 255, Math.max((color >>> 8) & 255, color & 255)));
            }
        }
        return peak;
    }
}
