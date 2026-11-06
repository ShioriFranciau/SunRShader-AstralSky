package dev.astralsky.client.config;

import dev.astralsky.client.AstralSkyClient;
import dev.astralsky.client.config.AstralSkyConfig.TimeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Single source of truth for observer position and astronomical time. */
public final class AstralSkyConfigScreen extends Screen {
    private static final int SLIDER_WIDTH = 128;
    private static final int FIELD_WIDTH = 60;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_WIDTH = SLIDER_WIDTH + FIELD_WIDTH + 8;
    private static final int COLUMN_GAP = 10;
    private static final int TOTAL_WIDTH = ROW_WIDTH * 2 + COLUMN_GAP;

    private final Screen parent;
    private final AstralSkyConfig original;
    private TimeMode timeMode;
    private Button timeModeButton;
    private NumericRow latitude;
    private NumericRow longitude;
    private NumericRow utcOffset;
    private NumericRow year;
    private NumericRow month;
    private NumericRow day;
    private NumericRow localHour;
    private NumericRow northOffset;

    public AstralSkyConfigScreen(Screen parent) {
        super(Component.translatable("screen.astralsky.title"));
        this.parent = parent;
        this.original = AstralSkyConfigStore.load();
        this.timeMode = original.timeMode();
    }

    @Override
    protected void init() {
        int left = (width - TOTAL_WIDTH) / 2;
        int top = Math.max(42, (height - ROW_HEIGHT * 7 - 20) / 2);
        int right = left + ROW_WIDTH + COLUMN_GAP;

        timeModeButton = addRenderableWidget(Button.builder(modeMessage(), button -> cycleTimeMode())
            .bounds(left, top, TOTAL_WIDTH, 20).build());
        latitude = addNumericRow(left, top + ROW_HEIGHT, "screen.astralsky.latitude",
            original.latitudeDegrees(), -90.0, 90.0, false);
        longitude = addNumericRow(left, top + ROW_HEIGHT * 2, "screen.astralsky.longitude",
            original.longitudeDegreesEast(), -180.0, 180.0, false);
        northOffset = addNumericRow(left, top + ROW_HEIGHT * 3, "screen.astralsky.north_offset",
            original.northOffsetDegrees(), -180.0, 180.0, false);

        utcOffset = addNumericRow(right, top + ROW_HEIGHT, "screen.astralsky.utc_offset",
            original.utcOffsetHours(), -14.0, 14.0, false);
        year = addNumericRow(right, top + ROW_HEIGHT * 2, "screen.astralsky.year",
            original.fixedYear(), 1900.0, 2100.0, true);
        month = addNumericRow(right, top + ROW_HEIGHT * 3, "screen.astralsky.month",
            original.fixedMonth(), 1.0, 12.0, true);
        day = addNumericRow(right, top + ROW_HEIGHT * 4, "screen.astralsky.day",
            original.fixedDay(), 1.0, 31.0, true);
        localHour = addNumericRow(right, top + ROW_HEIGHT * 5, "screen.astralsky.local_hour",
            original.fixedLocalHour(), 0.0, 23.99, false);

        int buttonsY = top + ROW_HEIGHT * 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
            .bounds(width / 2 - 104, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 4, buttonsY, 100, 20).build());
        updateModeAvailability();
    }

    private NumericRow addNumericRow(
        int left,
        int y,
        String labelKey,
        double initial,
        double minimum,
        double maximum,
        boolean integral
    ) {
        EditBox field = new EditBox(
            font, left + SLIDER_WIDTH + 8, y, FIELD_WIDTH, 20, Component.translatable(labelKey)
        );
        field.setMaxLength(16);
        field.setValue(format(initial, integral));
        addRenderableWidget(field);

        DoubleConsumer updateField = value -> field.setValue(format(value, integral));
        NumericSlider slider = addRenderableWidget(new NumericSlider(
            left, y, SLIDER_WIDTH, labelKey, initial, minimum, maximum, integral, updateField
        ));
        return new NumericRow(slider, field);
    }

    private void cycleTimeMode() {
        timeMode = switch (timeMode) {
            case SYSTEM_REALTIME -> TimeMode.FIXED_DATETIME;
            case FIXED_DATETIME -> TimeMode.FIXED_START_GAME_ELAPSED;
            case FIXED_START_GAME_ELAPSED -> TimeMode.SYSTEM_REALTIME;
        };
        timeModeButton.setMessage(modeMessage());
        updateModeAvailability();
    }

    private Component modeMessage() {
        String valueKey = switch (timeMode) {
            case SYSTEM_REALTIME -> "screen.astralsky.mode.realtime";
            case FIXED_DATETIME -> "screen.astralsky.mode.fixed";
            case FIXED_START_GAME_ELAPSED -> "screen.astralsky.mode.advancing";
        };
        return Component.translatable("screen.astralsky.mode", Component.translatable(valueKey));
    }

    private void updateModeAvailability() {
        boolean fixed = timeMode == TimeMode.FIXED_DATETIME
            || timeMode == TimeMode.FIXED_START_GAME_ELAPSED;
        utcOffset.setActive(true);
        year.setActive(fixed);
        month.setActive(fixed);
        day.setActive(fixed);
        // In the advancing mode the local hour initializes the game clock on
        // save, so it stays editable like the fixed date fields.
        localHour.setActive(fixed);
    }

    private void save() {
        double requestedLocalHour = parse(localHour, original.fixedLocalHour());
        int advancingStartWorldDay = original.advancingStartWorldDay();
        int advancingStartWorldTime = original.advancingStartWorldTime();
        if (timeMode == TimeMode.FIXED_START_GAME_ELAPSED) {
            int[] anchor = initializeGameClockAndCaptureAnchor(requestedLocalHour);
            advancingStartWorldDay = anchor[0];
            advancingStartWorldTime = anchor[1];
        }
        AstralSkyConfig updated = new AstralSkyConfig(
            AstralSkyConfig.CURRENT_SCHEMA_VERSION,
            timeMode,
            parse(latitude, original.latitudeDegrees()),
            parse(longitude, original.longitudeDegreesEast()),
            parse(utcOffset, original.utcOffsetHours()),
            (int) Math.round(parse(year, original.fixedYear())),
            (int) Math.round(parse(month, original.fixedMonth())),
            (int) Math.round(parse(day, original.fixedDay())),
            requestedLocalHour,
            parse(northOffset, original.northOffsetDegrees()),
            advancingStartWorldDay,
            advancingStartWorldTime
        ).validated();
        AstralSkyConfigStore.save(updated);
        ShaderConfigBridge.sync(updated);
        onClose();
        IrisReloadBridge.reloadCurrentPipeline();
    }

    /**
     * Initialises the game clock to the requested local civil hour (tick 0 is
     * 06:00, converted with no solar-time correction) on the integrated
     * server, keeping the current world day, and captures that instant as the
     * advancing anchor. A dedicated server keeps its current clock; the anchor
     * then marks the untouched world time.
     */
    private int[] initializeGameClockAndCaptureAnchor(double localHour) {
        long dayTime = Minecraft.getInstance().level == null
            ? 0L
            : Minecraft.getInstance().level.getOverworldClockTime();
        long worldDay = Math.max(0L, Math.floorDiv(dayTime, 24_000L));
        int ticksOfDay = AstralSkyConfig.localHourToGameTicks(localHour);

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            Holder.Reference<WorldClock> overworldClock = server.registryAccess()
                .lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
            server.clockManager().setTotalTicks(overworldClock, worldDay * 24_000L + ticksOfDay);
            AstralSkyClient.LOGGER.info(
                "Initialized the game clock to local hour {} (tick {} of day {})",
                localHour, ticksOfDay, worldDay
            );
        } else {
            AstralSkyClient.LOGGER.warn(
                "No integrated server: the game clock keeps its current time and only the anchor was captured"
            );
            ticksOfDay = (int) Math.floorMod(dayTime, 24_000L);
        }
        return new int[] { (int) worldDay, ticksOfDay };
    }

    private static double parse(NumericRow row, double fallback) {
        try {
            double value = Double.parseDouble(row.field().getValue().trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String format(double value, boolean integral) {
        return integral
            ? Integer.toString((int) Math.round(value))
            : String.format(Locale.ROOT, "%.4f", value);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(
            font,
            Component.translatable("screen.astralsky.hint"),
            width / 2,
            29,
            0xFFA0A0A0
        );
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private record NumericRow(NumericSlider slider, EditBox field) {
        void setActive(boolean active) {
            slider.active = active;
            field.active = active;
            field.setEditable(active);
        }
    }

    private static final class NumericSlider extends AbstractSliderButton {
        private final String labelKey;
        private final double minimum;
        private final double maximum;
        private final boolean integral;
        private final DoubleConsumer consumer;

        NumericSlider(
            int x,
            int y,
            int width,
            String labelKey,
            double initial,
            double minimum,
            double maximum,
            boolean integral,
            DoubleConsumer consumer
        ) {
            super(x, y, width, 20, Component.empty(), (initial - minimum) / (maximum - minimum));
            this.labelKey = labelKey;
            this.minimum = minimum;
            this.maximum = maximum;
            this.integral = integral;
            this.consumer = consumer;
            updateMessage();
        }

        private double current() {
            double raw = minimum + value * (maximum - minimum);
            return integral ? Math.rint(raw) : Math.rint(raw * 100.0) / 100.0;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(labelKey, format(current(), integral)));
        }

        @Override
        protected void applyValue() {
            consumer.accept(current());
        }
    }
}
