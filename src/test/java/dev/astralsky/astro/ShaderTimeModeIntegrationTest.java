package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shader-side gating that keeps sky colours, cloud brightness and fog
 * on the astronomical Sun and Moon instead of Minecraft's vanilla celestial
 * path. Without these branches, /time set would still steer the scene lighting
 * in the fixed and real-time observation modes.
 */
class ShaderTimeModeIntegrationTest {
    private static final Path SHADERS = Path.of(
        "shaderpack/SunRShader-AstralSky/shaders"
    );

    private static String read(String path) throws Exception {
        return Files.readString(SHADERS.resolve(path));
    }

    @Test
    void astronomicalLightExposesVanillaTimeMirrors() throws Exception {
        String source = read("include/lighting/astronomical_light.glsl");

        assertTrue(source.contains("float astro_day_factor()"));
        assertTrue(source.contains("float astro_blue_hour()"));
        assertTrue(source.contains("void astro_time_weights("));
        // The mirrors must reuse the same celestial vector as the discs.
        assertTrue(source.contains("astro_sun_direction()"));
        // Double inclusion must stay harmless now that headers pull it in.
        assertTrue(source.contains("INCLUDE_LIGHTING_ASTRONOMICAL_LIGHT"));
    }

    @Test
    void volumetricCloudLightingTracksAstronomicalSun() throws Exception {
        String[] layers = {
            "include/sky/clouds/cumulus.glsl",
            "include/sky/clouds/cumulus_congestus.glsl",
            "include/sky/clouds/altocumulus.glsl",
            "include/sky/clouds/cirrus.glsl",
            "include/sky/blocky_clouds.glsl",
            "include/sky/clouds/noctilucent.glsl"
        };

        for (String layer : layers) {
            String source = read(layer);
            assertTrue(
                source.contains("defined ASTRO_REAL_SKY"),
                layer + " misses the ASTRO_REAL_SKY gate"
            );
            assertTrue(
                source.contains("astro_sun_direction()"),
                layer + " keeps the vanilla sun direction for lighting"
            );
        }
    }

    @Test
    void cloudTransitionWeightsTrackAstronomicalSun() throws Exception {
        for (String path : new String[] {
            "include/sky/clouds/altocumulus.glsl",
            "include/sky/clouds/common.glsl",
            "include/weather/fog.glsl",
            "include/weather/core.glsl",
            "include/lighting/colors/light_color.glsl"
        }) {
            String source = read(path);
            assertTrue(
                source.contains("astro_time_weights("),
                path + " keeps vanilla time_sunrise/time_sunset weights"
            );
        }
    }

    @Test
    void cloudDayFactorTracksAstronomicalSun() throws Exception {
        String cloudShadows = read("include/lighting/cloud_shadows.glsl");
        assertTrue(cloudShadows.contains("float day_factor = astro_day_factor();"));

        String cirrus = read("include/sky/clouds/cirrus.glsl");
        assertTrue(cirrus.contains("float day_factor = astro_day_factor();"));

        String weatherClouds = read("include/weather/clouds.glsl");
        assertTrue(weatherClouds.contains("astro_day_factor()"));
    }

    @Test
    void cloudAndAmbientSkyBackgroundTracksAstronomicalSun() throws Exception {
        String skyMapVertex = read("program/d0_sky_map.vsh");
        assertTrue(skyMapVertex.contains("astro_sun_dir"));
        assertTrue(skyMapVertex.contains("astro_moon_dir"));

        for (String path : new String[] {
            "program/d1_clouds.vsh",
            "program/d1_clouds.fsh"
        }) {
            String source = read(path);
            assertTrue(
                source.contains("astro_sun_dir"),
                path + " keeps the vanilla sky background directions"
            );
        }
    }

    @Test
    void fogScatteringTracksAstronomicalLight() throws Exception {
        for (String path : new String[] {
            "include/fog/overworld/raymarched.glsl",
            "include/fog/overworld/analytic.glsl"
        }) {
            String source = read(path);
            assertTrue(
                source.contains("vec3 light_dir = astro_light_direction();"),
                path + " keeps the vanilla fog light direction"
            );
        }
    }

    @Test
    void weatherAuroraAndPurkinjeTrackAstronomicalSun() throws Exception {
        String weatherColor = read("include/lighting/colors/weather_color.glsl");
        assertTrue(weatherColor.contains("astro_sun_direction()"));

        String aurora = read("include/sky/aurora_colors.glsl");
        assertTrue(aurora.contains("astro_sun_direction()"));

        String purkinje = read("include/misc/purkinje_shift.glsl");
        assertTrue(purkinje.contains("astro_sun_direction()"));

        String atmosphere = read("include/sky/atmosphere.glsl");
        assertTrue(atmosphere.contains("astro_blue_hour()"));
    }

    @Test
    void specularAndParallaxShadingTracksAstronomicalLight() throws Exception {
        String specular = read("include/lighting/specular_lighting.glsl");
        assertTrue(specular.contains("astro_sun_direction()"));

        String solid = read("program/gbuffers_all_solid.fsh");
        assertTrue(solid.contains("astro_light_direction()"));
    }

    @Test
    void astronomicalLightIncludeRemainsSingleInclusionSafe() throws Exception {
        // Headers guarded by ASTRO_REAL_SKY include astronomical_light; the
        // guard must keep programs that already include it compiling.
        String source = read("include/lighting/astronomical_light.glsl");
        long openings = source.lines()
            .filter(line -> line.contains("#define INCLUDE_LIGHTING_ASTRONOMICAL_LIGHT"))
            .count();
        assertTrue(openings == 1);
        assertTrue(source.trim().endsWith("#endif"));
    }

    @Test
    void realTimeModeDeclaresOsDateUniformsOnce() throws Exception {
        String astronomy = read("include/sky/astronomy.glsl");

        // The real-time mode evaluates currentDate/currentTime from every
        // stage, so the uniforms must be declared beside the observation-time
        // code; undeclared identifiers used to fail shader compilation in
        // exactly this mode.
        assertTrue(astronomy.contains("uniform ivec3 currentDate;"));
        assertTrue(astronomy.contains("uniform ivec3 currentTime;"));

        // No program may redeclare them: astronomy.glsl is included by many
        // stages that also carry their own uniform blocks, and duplicates
        // would fail compilation.
        try (var files = java.nio.file.Files.walk(SHADERS.resolve("program"))) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).toList()) {
                String source = java.nio.file.Files.readString(file);
                assertTrue(
                    !source.contains("uniform ivec3 currentDate"),
                    file + " duplicates the astronomy.glsl declaration"
                );
            }
        }
    }

    @Test
    void advancingModeAnchorsItsHourToTheCapturedGameClock() throws Exception {
        String astronomy = read("include/sky/astronomy.glsl");

        // The anchor instant must show the game clock implied by the captured
        // tick anchor (tick 0 = 06:00) so the sky stays aligned with /time,
        // and civil time must convert with the configured UTC offset instead
        // of an apparent-solar-time longitude conversion.
        assertTrue(astronomy.contains("float anchor_local_hour"));
        assertTrue(astronomy.contains(
            "float(ASTRO_ADVANCING_START_TIME) / 1000.0 + 6.0"
        ));
        assertTrue(astronomy.contains(
            "anchor_local_hour - ASTRO_UTC_OFFSET"
        ));

        // Neither the fixed local hour nor the solar-time longitude
        // conversion may leak into the advancing mode.
        String advancingBranch = astronomy.substring(
            astronomy.indexOf("#elif ASTRO_TIME_MODE == 2"),
            astronomy.indexOf("#else")
        );
        assertTrue(!advancingBranch.contains("ASTRO_FIXED_HOUR"));
        assertTrue(!advancingBranch.contains("ASTRO_LONGITUDE"));
    }

    @Test
    void gameClockObservationModeIsRemoved() throws Exception {
        String astronomy = read("include/sky/astronomy.glsl");
        assertTrue(!astronomy.contains("ASTRO_TIME_MODE == 3"));

        // The slider fallback and both language files must only offer the
        // three remaining modes.
        String config = read("lib/astro_config.glsl");
        assertTrue(config.contains("#define ASTRO_TIME_MODE 0 // [0 1 2]"));
        assertTrue(!config.contains("[0 1 2 3]"));
    }
}
