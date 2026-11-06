package dev.astralsky.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.astralsky.client.config.AstralSkyConfigScreen;
import dev.astralsky.client.config.AstralSkyConfigStore;
import dev.astralsky.client.config.ShaderConfigBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AstralSkyClient implements ClientModInitializer {
    public static final String MOD_ID = "astralsky";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        var config = AstralSkyConfigStore.load();
        ShaderConfigBridge.sync(config);
        var category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "configuration")
        );
        var openConfiguration = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.astralsky.open_configuration",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K,
            category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfiguration.consumeClick()) {
                client.setScreenAndShow(new AstralSkyConfigScreen(null));
            }
        });
        LOGGER.info(
            "AstralSky initialized: mode={}, latitude={}, longitude={}",
            config.timeMode(),
            config.latitudeDegrees(),
            config.longitudeDegreesEast()
        );
    }
}

