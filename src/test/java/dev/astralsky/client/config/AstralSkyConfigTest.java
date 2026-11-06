package dev.astralsky.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AstralSkyConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void clampsInvalidLeapDayAndNonFiniteCoordinates() {
        AstralSkyConfig config = new AstralSkyConfig(
            0,
            AstralSkyConfig.TimeMode.FIXED_DATETIME,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            99.0,
            2025,
            2,
            31,
            -2.0,
            -999.0,
            -5,
            25_000
        ).validated();

        assertEquals(AstralSkyConfig.CURRENT_SCHEMA_VERSION, config.schemaVersion());
        assertEquals(31.2304, config.latitudeDegrees());
        assertEquals(121.4737, config.longitudeDegreesEast());
        assertEquals(14.0, config.utcOffsetHours());
        assertEquals(28, config.fixedDay());
        assertEquals(0.0, config.fixedLocalHour());
        assertEquals(-180.0, config.northOffsetDegrees());
        assertEquals(0, config.advancingStartWorldDay());
        assertEquals(23_999, config.advancingStartWorldTime());
        assertFalse(Double.isNaN(config.latitudeDegrees()));
    }

    @Test
    void rendersArbitraryValuesForTheShaderInclude() {
        AstralSkyConfig config = new AstralSkyConfig(
            1,
            AstralSkyConfig.TimeMode.SYSTEM_REALTIME,
            -33.8688,
            151.2093,
            10.0,
            2030,
            7,
            4,
            19.25,
            12.5,
            123,
            4_567
        ).validated();

        String include = ShaderConfigBridge.render(config);
        assertTrue(include.contains("#define ASTRO_RUNTIME_CONFIG 1"));
        assertTrue(include.contains(
            "#define ASTRO_CFG_SCHEMA " + ShaderConfigBridge.RUNTIME_CONFIG_SCHEMA
        ));
        assertTrue(include.contains("#define ASTRO_CFG_TIME_MODE 0"));
        assertTrue(include.contains("#define ASTRO_CFG_LATITUDE -33.86880000"));
        assertTrue(include.contains("#define ASTRO_CFG_LONGITUDE 151.20930000"));
        assertTrue(include.contains("#define ASTRO_CFG_FIXED_HOUR 19.25000000"));
        assertTrue(include.contains("#define ASTRO_CFG_ADVANCING_START_DAY 123"));
        assertTrue(include.contains("#define ASTRO_CFG_ADVANCING_START_TIME 4567"));
    }

    @Test
    void packIgnoresRuntimeConfigFromIncompatibleCompanion() throws Exception {
        // The pack and the companion must agree on the option mapping: a
        // companion and pack from different generations used to silently
        // activate the wrong time mode.
        String packConfig = java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "shaderpack/SunRShader-AstralSky/shaders/lib/astro_config.glsl"
            )
        );
        assertTrue(packConfig.contains(
            "ASTRO_CFG_SCHEMA != " + ShaderConfigBridge.RUNTIME_CONFIG_SCHEMA
        ));
        assertTrue(packConfig.contains("#undef ASTRO_RUNTIME_CONFIG"));
    }

    @Test
    void migratingARemovedTimeModeKeepsTheOtherFields() {
        com.google.gson.JsonObject tree = com.google.gson.JsonParser.parseString(
            "{\"schemaVersion\":2,\"timeMode\":\"SYSTEM_DATE_GAME_CLOCK\","
                + "\"latitudeDegrees\":39.977,\"fixedLocalHour\":11.0}"
        ).getAsJsonObject();

        AstralSkyConfigStore.migrateUnknownTimeMode(tree);

        assertEquals(
            AstralSkyConfig.TimeMode.SYSTEM_REALTIME.name(),
            tree.get("timeMode").getAsString()
        );
        assertEquals(39.977, tree.get("latitudeDegrees").getAsDouble(), 1e-9);
        assertEquals(11.0, tree.get("fixedLocalHour").getAsDouble(), 1e-9);

        com.google.gson.JsonObject current = com.google.gson.JsonParser.parseString(
            "{\"timeMode\":\"FIXED_START_GAME_ELAPSED\"}"
        ).getAsJsonObject();
        AstralSkyConfigStore.migrateUnknownTimeMode(current);
        assertEquals(
            "FIXED_START_GAME_ELAPSED",
            current.get("timeMode").getAsString()
        );
    }

    @Test
    void rendersAdvancingGameTimeMode() {
        AstralSkyConfig config = new AstralSkyConfig(
            2,
            AstralSkyConfig.TimeMode.FIXED_START_GAME_ELAPSED,
            32.7767,
            -96.7970,
            -5.0,
            2024,
            4,
            8,
            13.25,
            0.0,
            42,
            6_000
        ).validated();

        assertTrue(ShaderConfigBridge.render(config).contains("#define ASTRO_CFG_TIME_MODE 2"));
    }

    @Test
    void convertsLocalHoursToGameTicksWithoutSolarTimeCorrection() {
        // Tick 0 is 06:00; one astronomical hour is 1000 ticks.
        assertEquals(16_000, AstralSkyConfig.localHourToGameTicks(22.0));
        assertEquals(0, AstralSkyConfig.localHourToGameTicks(6.0));
        assertEquals(23_000, AstralSkyConfig.localHourToGameTicks(5.0));
        assertEquals(18_000, AstralSkyConfig.localHourToGameTicks(0.0));
        assertEquals(17_500, AstralSkyConfig.localHourToGameTicks(23.5));
        assertEquals(12_000, AstralSkyConfig.localHourToGameTicks(18.0));
    }

    @Test
    void synchronizesRuntimeConfigurationIntoPackagedZip() throws Exception {
        Path archivePath = temporaryDirectory.resolve("SunRShader-AstralSky.zip");
        try (var archive = FileSystems.newFileSystem(
            java.net.URI.create("jar:" + archivePath.toUri()),
            Map.of("create", "true")
        )) {
            Path identity = archive.getPath("/shaders/lib/astro_config.glsl");
            Files.createDirectories(identity.getParent());
            Files.writeString(identity, "// identity", StandardCharsets.UTF_8);
        }

        String content = ShaderConfigBridge.render(AstralSkyConfig.defaults());
        assertTrue(ShaderConfigBridge.syncPack(archivePath, content));

        try (var archive = FileSystems.newFileSystem(archivePath, Map.of())) {
            assertEquals(
                content,
                Files.readString(
                    archive.getPath("/shaders/lib/astro_runtime_config.glsl"),
                    StandardCharsets.UTF_8
                )
            );
        }
    }
}
