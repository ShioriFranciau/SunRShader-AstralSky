package dev.astralsky.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.astralsky.client.AstralSkyClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class AstralSkyConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "astralsky.json";

    private AstralSkyConfigStore() {
    }

    public static AstralSkyConfig load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            AstralSkyConfig defaults = AstralSkyConfig.defaults();
            save(defaults);
            return defaults;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject tree = GSON.fromJson(json, JsonObject.class);
            if (tree == null) {
                throw new IOException("Configuration file contains JSON null");
            }
            migrateUnknownTimeMode(tree);
            AstralSkyConfig parsed = GSON.fromJson(tree, AstralSkyConfig.class);
            AstralSkyConfig validated = parsed.validated();
            if (!validated.equals(parsed)) {
                save(validated);
            }
            return validated;
        } catch (Exception exception) {
            AstralSkyClient.LOGGER.error("Unable to read {}; using defaults", path, exception);
            return AstralSkyConfig.defaults();
        }
    }

    /**
     * Configurations written by companions that still shipped removed time
     * modes keep every other field and only fall back on the mode itself.
     */
    static JsonObject migrateUnknownTimeMode(JsonObject tree) {
        if (tree == null || !tree.has("timeMode")) {
            return tree;
        }
        JsonElement mode = tree.get("timeMode");
        if (!mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isString()) {
            return tree;
        }
        String name = mode.getAsString();
        boolean known = Arrays.stream(AstralSkyConfig.TimeMode.values())
            .anyMatch(candidate -> candidate.name().equals(name));
        if (known) {
            return tree;
        }
        AstralSkyClient.LOGGER.warn(
            "Configuration uses removed time mode {}; falling back to SYSTEM_REALTIME",
            name
        );
        tree.addProperty("timeMode", AstralSkyConfig.TimeMode.SYSTEM_REALTIME.name());
        return tree;
    }

    public static void save(AstralSkyConfig config) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config.validated()) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AstralSkyClient.LOGGER.error("Unable to write {}", path, exception);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}

