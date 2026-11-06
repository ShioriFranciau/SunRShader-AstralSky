package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderMoonIntegrationTest {
    @Test
    void moonUsesLroTextureAndCalculatedBodyOrientation() throws Exception {
        String astronomy = Files.readString(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/include/sky/astronomy.glsl"
        ));
        String sky = Files.readString(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/include/sky/sky.glsl"
        ));
        String properties = Files.readString(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/shaders.properties"
        ));

        assertTrue(astronomy.contains("astro_lunar_body_axes"));
        assertTrue(astronomy.contains("13.17635815"));
        assertTrue(sky.contains("texture(moon_sampler, moon_uv)"));
        assertTrue(sky.contains("lunar_prime_meridian"));
        assertTrue(properties.contains("lroc-moon-2k.png"));
        assertTrue(Files.size(Path.of(
            "shaderpack/SunRShader-AstralSky/shaders/textures/lroc-moon-2k.png"
        )) > 1_000_000);
    }
}
