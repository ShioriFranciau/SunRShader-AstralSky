#include "/lib/astro_runtime_config.glsl"

#if defined ASTRO_RUNTIME_CONFIG && ASTRO_CFG_SCHEMA != 3
    // The companion wrote this file with an incompatible option mapping.
    // Ignore it until the companion re-syncs, so a stale or mismatched
    // runtime configuration cannot silently activate the wrong time mode.
    #undef ASTRO_RUNTIME_CONFIG
#endif

#ifdef ASTRO_RUNTIME_CONFIG
    #define ASTRO_LATITUDE ASTRO_CFG_LATITUDE
    #define ASTRO_LONGITUDE ASTRO_CFG_LONGITUDE
    #define ASTRO_NORTH_OFFSET ASTRO_CFG_NORTH_OFFSET
    #define ASTRO_TIME_MODE ASTRO_CFG_TIME_MODE
    #define ASTRO_UTC_OFFSET ASTRO_CFG_UTC_OFFSET
    #define ASTRO_FIXED_YEAR ASTRO_CFG_FIXED_YEAR
    #define ASTRO_FIXED_MONTH ASTRO_CFG_FIXED_MONTH
    #define ASTRO_FIXED_DAY ASTRO_CFG_FIXED_DAY
    #define ASTRO_FIXED_HOUR ASTRO_CFG_FIXED_HOUR
    #define ASTRO_ADVANCING_START_DAY ASTRO_CFG_ADVANCING_START_DAY
    #define ASTRO_ADVANCING_START_TIME ASTRO_CFG_ADVANCING_START_TIME
#else
    #define ASTRO_LATITUDE 31.23 // [-75.0 -60.0 -45.0 -30.0 -15.0 0.0 15.0 30.0 31.23 45.0 60.0 75.0]
    #define ASTRO_LONGITUDE 121.47 // [-180.0 -150.0 -120.0 -90.0 -60.0 -30.0 0.0 30.0 60.0 90.0 120.0 121.47 150.0 180.0]
    #define ASTRO_NORTH_OFFSET 0.0 // [-180.0 -135.0 -90.0 -45.0 0.0 45.0 90.0 135.0 180.0]

    #define ASTRO_TIME_MODE 0 // [0 1 2]
    #define ASTRO_UTC_OFFSET 8.0 // [-12.0 -10.0 -8.0 -6.0 -5.0 -4.0 0.0 1.0 2.0 3.0 5.5 8.0 9.0 10.0 12.0 14.0]
    #define ASTRO_FIXED_YEAR 2026 // [1900 1950 2000 2010 2020 2024 2025 2026 2030 2050 2100]
    #define ASTRO_FIXED_MONTH 10 // [1 2 3 4 5 6 7 8 9 10 11 12]
    #define ASTRO_FIXED_DAY 30 // [1 5 10 15 20 25 28 29 30 31]
    #define ASTRO_FIXED_HOUR 22.0 // [0.0 2.0 4.0 6.0 8.0 10.0 12.0 14.0 16.0 18.0 20.0 22.0 23.0]
    #define ASTRO_ADVANCING_START_DAY 0
    #define ASTRO_ADVANCING_START_TIME 0
#endif

#define ASTRO_CATALOG_MODE 1 // [0 1 2]
#define ASTRO_CATALOG_BRIGHTNESS 0.75 // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define ASTRO_FAINT_STAR_STRENGTH 0.75 // [0.0 0.25 0.5 0.75 1.0 1.25]
#define ASTRO_STAR_COLOR 1.0 // [0.0 0.5 0.75 1.0 1.25]
#define ASTRO_MILKY_WAY_BRIGHTNESS 1.0 // [0.0 0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define ASTRO_TWINKLE 0.10 // [0.0 0.05 0.10 0.15 0.20]
#define ASTRO_MOON_SIZE 1.5 // [1.0 1.5 2.0 2.5 3.0]
#define ASTRO_EARTHSHINE 0.06 // [0.0 0.03 0.06 0.10 0.15]
#define ASTRO_PLANET_BRIGHTNESS 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0 5.0 8.0]
#define ASTRO_PLANET_SIZE 1.0 // [1.0 1.5 2.0 3.0 5.0]
// The physical discs are smaller than one pixel at normal fields of view.
#define ASTRO_PLANET_MIN_RADIUS 0.0007

