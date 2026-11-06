#if !defined INCLUDE_LIGHTING_COLORS_LIGHT_COLOR
#define INCLUDE_LIGHTING_COLORS_LIGHT_COLOR

#include "/include/sky/atmosphere.glsl"
#include "/include/utility/color.glsl"

#if defined ASTRO_REAL_SKY && defined WORLD_OVERWORLD
#include "/include/lighting/astronomical_light.glsl"
#endif

uniform float moon_phase_brightness;

// Magic brightness adjustment so that auto exposure isn't needed
float get_sun_exposure() {
    const float base_scale = 7.0 * SUN_I;

    float sun_height =
#ifdef ASTRO_REAL_SKY
        astro_sun_direction().y;
#else
        sun_dir.y;
#endif

    float blue_hour
        = linear_step(0.05, 1.0, exp(-190.0 * sqr(sun_height + 0.09604)));

#if defined ASTRO_REAL_SKY && defined WORLD_OVERWORLD
    float time_sunrise;
    float time_noon;
    float time_sunset;
    float time_midnight;
    astro_time_weights(time_sunrise, time_noon, time_sunset, time_midnight);
#endif

    float daytime_mul
        = 1.0 + 0.5 * (time_sunset + time_sunrise) + 40.0 * blue_hour;

    float eclipse_transmission =
#ifdef ASTRO_REAL_SKY
        astro_eclipse_light_transmission();
#else
        1.0;
#endif
    return base_scale * daytime_mul * eclipse_transmission;
}

vec3 get_sun_tint() {
    float sun_height =
#ifdef ASTRO_REAL_SKY
        astro_sun_direction().y;
#else
        sun_dir.y;
#endif

#if defined ASTRO_REAL_SKY && defined WORLD_OVERWORLD
    float time_sunrise;
    float time_noon;
    float time_sunset;
    float time_midnight;
    astro_time_weights(time_sunrise, time_noon, time_sunset, time_midnight);
#endif
    float blue_hour
        = linear_step(0.05, 1.0, exp(-190.0 * sqr(sun_height + 0.09604)));

    vec3 morning_evening_tint = vec3(1.05, 0.84, 0.93) * 1.2;
    morning_evening_tint = mix(
        vec3(1.0),
        morning_evening_tint,
        sqr(pulse(sun_height, 0.17, 0.40))
    );

    vec3 blue_hour_tint = vec3(0.95, 0.80, 1.0);
    blue_hour_tint = mix(vec3(1.0), blue_hour_tint, blue_hour);

    // User tint

    const vec3 tint_morning = from_srgb(vec3(SUN_MR, SUN_MG, SUN_MB));
    const vec3 tint_noon = from_srgb(vec3(SUN_NR, SUN_NG, SUN_NB));
    const vec3 tint_evening = from_srgb(vec3(SUN_ER, SUN_EG, SUN_EB));

    vec3 user_tint = mix(tint_noon, tint_morning, time_sunrise);
    user_tint = mix(user_tint, tint_evening, time_sunset);

    return morning_evening_tint * blue_hour_tint * user_tint;
}

float get_moon_exposure() {
    const float base_scale = 0.66 * MOON_I;

    float sun_height =
#ifdef ASTRO_REAL_SKY
        astro_sun_direction().y;
#else
        sun_dir.y;
#endif

    float time_boost = 1.0 + 0.33 * rcp(clamp01(1.25 * max(-sun_height, 0.1)));

    return base_scale * moon_phase_brightness * time_boost;
}

vec3 get_moon_tint() {
    const vec3 base_tint = from_srgb(vec3(MOON_R, MOON_G, MOON_B));

    return base_tint;
}

vec3 get_light_color() {
    vec3 direct_light_direction =
#ifdef ASTRO_REAL_SKY
        astro_light_direction();
#else
        light_dir;
#endif
    float sun_is_below_horizon =
#ifdef ASTRO_REAL_SKY
        astro_sun_direction().y < 0.0 ? 1.0 : 0.0;
#else
        step(0.5, sunAngle);
#endif
    vec3 light_color
        = sunlight_color * atmosphere_transmittance(
            direct_light_direction.y, planet_radius
        );
    light_color = atmosphere_post_processing(light_color);
    light_color *= mix(
        get_sun_exposure() * get_sun_tint(),
        get_moon_exposure() * get_moon_tint(),
        sun_is_below_horizon
    );
    light_color *= clamp01(
        rcp(0.02) * direct_light_direction.y
    ); // fade away during day/night transition
    light_color *= 1.0
        - 0.25 * pulse(abs(direct_light_direction.y), 0.15, 0.11);

    return light_color;
}

float get_skylight_boost() {
    float sun_height =
#ifdef ASTRO_REAL_SKY
        astro_sun_direction().y;
#else
        sun_dir.y;
#endif
    float early_night
        = linear_step(0.05, 1.0, exp(-25.0 * sqr(sun_height + 0.3)));
    return 1.0 + 0.5 * early_night;
}

#endif // INCLUDE_LIGHTING_COLORS_LIGHT_COLOR
