#if !defined INCLUDE_LIGHTING_ASTRONOMICAL_LIGHT
#define INCLUDE_LIGHTING_ASTRONOMICAL_LIGHT

#define ASTRO_POSITION_ONLY
#include "/include/sky/astronomy.glsl"
#undef ASTRO_POSITION_ONLY

// Keep direct lighting and the shadow camera on the same celestial vector as
// the rendered Sun and Moon. Iris' built-in light vectors and shadow matrices
// follow Minecraft's vanilla celestial path, which cannot represent a real
// observer latitude and solar declination.
void astro_light_directions(
    out vec3 astronomical_sun_direction,
    out vec3 astronomical_moon_direction
) {
    float lunar_distance;
    astro_celestial_directions(
        astronomical_sun_direction,
        astronomical_moon_direction,
        lunar_distance
    );
}

vec3 astro_sun_direction() {
    vec3 astronomical_sun_direction;
    vec3 astronomical_moon_direction;
    astro_light_directions(
        astronomical_sun_direction, astronomical_moon_direction
    );
    return astronomical_sun_direction;
}

vec3 astro_moon_direction() {
    vec3 astronomical_sun_direction;
    vec3 astronomical_moon_direction;
    astro_light_directions(
        astronomical_sun_direction, astronomical_moon_direction
    );
    return astronomical_moon_direction;
}

vec3 astro_light_direction() {
    vec3 astronomical_sun_direction;
    vec3 astronomical_moon_direction;
    astro_light_directions(
        astronomical_sun_direction, astronomical_moon_direction
    );

    // Use the real Sun while it is above the geometric horizon, then the real
    // Moon for Photon's night lighting and shadow pass.
    return astronomical_sun_direction.y >= 0.0
        ? astronomical_sun_direction
        : astronomical_moon_direction;
}

// Mirrors of the vanilla-time custom uniforms from shaders.properties, driven
// by the same astronomical Sun as the rendered celestial discs. Without these,
// Minecraft's /time command would still steer sky colours, cloud brightness
// and fog towards the vanilla celestial path even in fixed or real-time
// observation modes.

float astro_day_factor() {
    float clamped_height = clamp(abs(astro_sun_direction().y) / 0.3, 0.0, 1.0);
    return clamped_height * clamped_height * (3.0 - 2.0 * clamped_height);
}

float astro_blue_hour() {
    float sun_height = astro_sun_direction().y;
    return exp(-150.0 * (sun_height + 0.07283) * (sun_height + 0.07283));
}

void astro_time_weights(
    out float time_sunrise,
    out float time_noon,
    out float time_sunset,
    out float time_midnight
) {
    vec3 sun_direction = astro_sun_direction();

    float fade = sun_direction.y < 0.18
        ? 0.37 + 1.2 * max(0.0, -sun_direction.y)
        : 1.7;
    float weight
        = pow(clamp(1.0 - fade * abs(sun_direction.y - 0.18), 0.0, 1.0), 2.0);

    time_sunrise  = (sun_direction.x > 0.0 ? 1.0 : 0.0) * weight;
    time_noon     = (sun_direction.y > 0.0 ? 1.0 : 0.0) * (1.0 - weight);
    time_midnight = (sun_direction.y < 0.0 ? 1.0 : 0.0) * (1.0 - weight);
    time_sunset   = (sun_direction.x < 0.0 ? 1.0 : 0.0) * weight;
}

mat3 astro_shadow_world_to_view_rotation() {
    vec3 light_direction = astro_light_direction();
    vec3 reference_up = abs(light_direction.y) > 0.999
        ? vec3(0.0, 0.0, 1.0)
        : vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(reference_up, light_direction));
    vec3 up = normalize(cross(light_direction, right));

    // Rows are the light camera's right, up and backward axes. Consequently
    // view-space -Z points from the light towards the scene.
    return transpose(mat3(right, up, light_direction));
}

vec3 astro_scene_to_shadow_view(vec3 scene_position, mat4 vanilla_shadow_view) {
    return astro_shadow_world_to_view_rotation() * scene_position
        + vanilla_shadow_view[3].xyz;
}

vec3 astro_shadow_view_to_scene(vec3 shadow_position, mat4 vanilla_shadow_view) {
    return transpose(astro_shadow_world_to_view_rotation())
        * (shadow_position - vanilla_shadow_view[3].xyz);
}

#endif

