package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderEclipseIntegrationTest {
    private static final Path PACK = Path.of("shaderpack", "SunRShader-AstralSky", "shaders");

    @Test
    void shaderUsesTopocentricMoonForEclipseCoverage() throws Exception {
        String astronomy = Files.readString(PACK.resolve("include/sky/astronomy.glsl"));
        String sky = Files.readString(PACK.resolve("include/sky/sky.glsl"));
        String light = Files.readString(PACK.resolve("include/lighting/colors/light_color.glsl"));

        assertTrue(astronomy.contains("geocentric_moon_direction * lunar.z"));
        assertTrue(astronomy.contains("astro_solar_eclipse_coverage"));
        assertTrue(astronomy.contains("astro_eclipse_display_scale"));
        assertTrue(astronomy.contains("atan("));
        assertTrue(astronomy.contains("length(cross(first, second))"));
        assertTrue(sky.contains("float moon_disc"));
        assertTrue(sky.contains("corona_profile"));
        assertTrue(sky.contains("lunar_silhouette"));
        assertTrue(sky.contains("fwidth(moon_view_angle)"));
        assertTrue(sky.contains("fwidth(eclipse_moon_angle)"));
        assertTrue(light.contains("astro_eclipse_light_transmission"));
    }

    @Test
    void shaderAdvancesFromCapturedWorldClockAnchor() throws Exception {
        String astronomy = Files.readString(PACK.resolve("include/sky/astronomy.glsl"));
        assertTrue(astronomy.contains("#elif ASTRO_TIME_MODE == 2"));
        assertTrue(astronomy.contains("astro_world_day - ASTRO_ADVANCING_START_DAY"));
        assertTrue(astronomy.contains("astro_world_time - ASTRO_ADVANCING_START_TIME"));
    }
}
