package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderShadowIntegrationTest {
    private static final Path SHADERS = Path.of(
        "shaderpack/SunRShader-AstralSky/shaders"
    );

    @Test
    void realSkyDrivesBothShadowMapAndShadowSampling() throws Exception {
        String shadowVertex = Files.readString(SHADERS.resolve("program/shadow.vsh"));
        String shadowSampling = Files.readString(
            SHADERS.resolve("include/lighting/shadows/pcss.glsl")
        );
        String deferred = Files.readString(
            SHADERS.resolve("program/d4_deferred_shading.fsh")
        );

        assertTrue(shadowVertex.contains("astro_scene_to_shadow_view"));
        assertTrue(shadowSampling.contains("astro_scene_to_shadow_view"));
        assertTrue(shadowSampling.contains("astro_light_direction()"));
        assertTrue(deferred.contains("shading_light_direction"));
        assertTrue(deferred.contains("astro_light_direction()"));
    }

    @Test
    void astronomicalShadowCameraUsesLightAsItsBackwardAxis() throws Exception {
        String source = Files.readString(
            SHADERS.resolve("include/lighting/astronomical_light.glsl")
        );
        assertTrue(source.contains("transpose(mat3(right, up, light_direction))"));
        assertTrue(source.contains("astronomical_sun_direction.y >= 0.0"));
    }

    @Test
    void everyProgramUsingRealLightColoursIncludesAstronomicalDirections() throws Exception {
        try (Stream<Path> files = Files.walk(SHADERS.resolve("program"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(file);
                if (source.contains("/include/lighting/colors/light_color.glsl")) {
                    assertTrue(
                        source.contains("/include/lighting/astronomical_light.glsl"),
                        file.toString()
                    );
                }
            }
        }
    }
}
