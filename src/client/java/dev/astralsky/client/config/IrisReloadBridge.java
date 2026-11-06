package dev.astralsky.client.config;

import dev.astralsky.client.AstralSkyClient;

import java.lang.reflect.InvocationTargetException;

/** Optional runtime bridge to Iris without making Iris a hard mod dependency. */
public final class IrisReloadBridge {
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private IrisReloadBridge() {
    }

    public static boolean reloadCurrentPipeline() {
        try {
            Class<?> apiClass = Class.forName(IRIS_API_CLASS);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object config = apiClass.getMethod("getConfig").invoke(api);
            Class<?> configClass = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            boolean enabled = (boolean) configClass.getMethod("areShadersEnabled").invoke(config);

            // Iris.reload() is called even when the enabled value is unchanged.
            // This rebuilds the pipeline and re-reads astro_runtime_config.glsl.
            configClass.getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
            AstralSkyClient.LOGGER.info("Reloaded the Iris pipeline after astronomical settings changed");
            return true;
        } catch (ClassNotFoundException exception) {
            AstralSkyClient.LOGGER.warn("Iris is not installed; astronomical settings will apply on next launch");
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            AstralSkyClient.LOGGER.error("Installed Iris does not expose the expected v0 reload API", exception);
        } catch (InvocationTargetException exception) {
            AstralSkyClient.LOGGER.error("Iris failed to rebuild the shader pipeline", exception.getCause());
        } catch (LinkageError | RuntimeException exception) {
            AstralSkyClient.LOGGER.error("Unable to invoke the Iris reload API", exception);
        }
        return false;
    }
}
