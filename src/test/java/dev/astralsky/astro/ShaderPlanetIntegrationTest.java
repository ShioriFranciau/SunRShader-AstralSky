package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wiring between the VSOP87 table, the deferred compute pass that
 * fills the planet image, and the sky path that draws it.
 */
class ShaderPlanetIntegrationTest {
    private static final Path SHADERS = Path.of(
        "shaderpack/SunRShader-AstralSky/shaders"
    );

    @Test
    void computePassFillsThePlanetImageBeforeAnyEarlyExit() throws Exception {
        String compute = Files.readString(
            SHADERS.resolve("program/d4a_generate_sky_sh.csh")
        );

        assertTrue(compute.contains("#include \"/include/sky/planet_state.glsl\""));
        assertTrue(compute.contains("#include \"/include/sky/vsop87.glsl\"") || compute.contains(
            "planet_state.glsl"
        ));

        int store = compute.indexOf("astro_store_planet_data()");
        int earlyExit = compute.indexOf("#ifndef SH_SKYLIGHT");
        assertTrue(store > 0, "the compute pass must fill the planet image");
        assertTrue(earlyExit > 0, "the skylight early exit must still exist");
        assertTrue(
            store < earlyExit,
            "planet data must be written even when skylight SH is disabled"
        );
    }

    @Test
    void planetStateUsesThePositionOnlyAstronomyInclude() throws Exception {
        String state = Files.readString(SHADERS.resolve("include/sky/planet_state.glsl"));

        assertTrue(state.contains("void astro_store_planet_data()"));
        assertTrue(state.contains("imageStore("));
        assertTrue(state.contains("layout(rgba32f) writeonly uniform image2D planet_data_img;"));
        // The compute pass has no star or lunar samplers.
        assertTrue(state.contains("ASTRO_POSITION_ONLY"));
        assertTrue(state.contains("VSOP87"));
    }

    @Test
    void skyPathDrawsPlanetsFromTheSampler() throws Exception {
        String sky = Files.readString(SHADERS.resolve("include/sky/sky.glsl"));
        String planets = Files.readString(SHADERS.resolve("include/sky/planets.glsl"));

        assertTrue(sky.contains("#include \"/include/sky/planets.glsl\""));
        assertTrue(sky.contains("astro_draw_planets(ray_dir)"));
        assertTrue(planets.contains("uniform sampler2D planet_data_sampler;"));
        assertTrue(planets.contains("texelFetch(planet_data_sampler"));
        assertTrue(planets.contains("ASTRO_PLANET_MIN_RADIUS"));
        assertTrue(planets.contains("astro_night_visibility()"));
    }

    @Test
    void imageAndOptionsAreRegistered() throws Exception {
        String properties = Files.readString(SHADERS.resolve("shaders.properties"));
        String config = Files.readString(SHADERS.resolve("lib/astro_config.glsl"));
        String settings = Files.readString(SHADERS.resolve("settings.glsl"));

        assertTrue(properties.contains(
            "image.planet_data_img = planet_data_sampler rgba rgba32f float false false 16 1"
        ));
        assertTrue(properties.contains("screen.astronomy        = ASTRO_REAL_SKY ASTRO_PLANETS"));
        assertTrue(properties.contains("ASTRO_PLANET_BRIGHTNESS ASTRO_PLANET_SIZE"));
        assertTrue(settings.contains("#define ASTRO_PLANETS"));
        assertTrue(config.contains("#define ASTRO_PLANET_BRIGHTNESS"));
        assertTrue(config.contains("#define ASTRO_PLANET_SIZE"));
        assertTrue(config.contains("#define ASTRO_PLANET_MIN_RADIUS"));
    }

    @Test
    void languageFilesDescribeTheNewOptions() throws Exception {
        for (String language : new String[] {"en_US", "zh_CN"}) {
            String text = Files.readString(SHADERS.resolve("lang/" + language + ".lang"));
            assertTrue(text.contains("option.ASTRO_PLANETS="), language);
            assertTrue(text.contains("option.ASTRO_PLANET_BRIGHTNESS="), language);
            assertTrue(text.contains("option.ASTRO_PLANET_SIZE="), language);
        }
    }

    @Test
    void generatedShaderTablesCarryEveryBodyAndCoordinate() throws Exception {
        String tables = Files.readString(SHADERS.resolve("include/sky/vsop87.glsl"));

        assertTrue(tables.contains("#if !defined INCLUDE_SKY_VSOP87"));
        assertTrue(tables.contains("vec3 vsop87_heliocentric(int body, float t)"));
        for (String body : new String[] {
            "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune", "earth"
        }) {
            assertTrue(tables.contains("void vsop87_" + body + "(float t"), body);
            String upper = body.toUpperCase(java.util.Locale.ROOT);
            for (String coordinate : new String[] {"L", "B", "R"}) {
                assertTrue(
                    tables.contains("const vec4 VSOP_" + upper + "_" + coordinate + "["),
                    body + " " + coordinate
                );
            }
        }
        // Generated data, not a hand-written stub.
        assertTrue(Files.size(SHADERS.resolve("include/sky/vsop87.glsl")) > 150_000);
        assertEquals(
            2_676,
            Vsop87Tables.BODIES[0].termCount()
                + Vsop87Tables.BODIES[1].termCount()
                + Vsop87Tables.BODIES[2].termCount()
                + Vsop87Tables.BODIES[3].termCount()
                + Vsop87Tables.BODIES[4].termCount()
                + Vsop87Tables.BODIES[5].termCount()
                + Vsop87Tables.BODIES[6].termCount()
                + Vsop87Tables.BODIES[7].termCount()
        );
    }
}
