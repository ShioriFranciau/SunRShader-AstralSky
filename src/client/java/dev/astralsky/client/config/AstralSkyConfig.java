package dev.astralsky.client.config;

import java.time.YearMonth;

public record AstralSkyConfig(
    int schemaVersion,
    TimeMode timeMode,
    double latitudeDegrees,
    double longitudeDegreesEast,
    double utcOffsetHours,
    int fixedYear,
    int fixedMonth,
    int fixedDay,
    double fixedLocalHour,
    double northOffsetDegrees,
    int advancingStartWorldDay,
    int advancingStartWorldTime
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public static AstralSkyConfig defaults() {
        return new AstralSkyConfig(
            CURRENT_SCHEMA_VERSION,
            TimeMode.SYSTEM_REALTIME,
            31.2304,
            121.4737,
            8.0,
            2026,
            10,
            30,
            22.0,
            0.0,
            0,
            0
        );
    }

    public AstralSkyConfig validated() {
        int year = clamp(fixedYear, 1900, 2100);
        int month = clamp(fixedMonth, 1, 12);
        int day = clamp(fixedDay, 1, YearMonth.of(year, month).lengthOfMonth());

        return new AstralSkyConfig(
            CURRENT_SCHEMA_VERSION,
            timeMode == null ? TimeMode.SYSTEM_REALTIME : timeMode,
            clampFinite(latitudeDegrees, -90.0, 90.0, 31.2304),
            clampFinite(longitudeDegreesEast, -180.0, 180.0, 121.4737),
            clampFinite(utcOffsetHours, -14.0, 14.0, 8.0),
            year,
            month,
            day,
            clampFinite(fixedLocalHour, 0.0, 23.999_999, 22.0),
            clampFinite(northOffsetDegrees, -180.0, 180.0, 0.0),
            Math.max(advancingStartWorldDay, 0),
            clamp(advancingStartWorldTime, 0, 23_999)
        );
    }

    /**
     * Minecraft tick-of-day for a local civil hour; tick 0 is 06:00 and one
     * astronomical hour is 1000 ticks. No apparent-solar-time correction is
     * applied, so the game clock reads the configured local hour directly.
     */
    public static int localHourToGameTicks(double localHour) {
        return (int) Math.floorMod(Math.round((localHour - 6.0) * 1000.0), 24_000L);
    }

    private static double clampFinite(double value, double minimum, double maximum, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum TimeMode {
        SYSTEM_REALTIME,
        FIXED_DATETIME,
        FIXED_START_GAME_ELAPSED
    }
}

