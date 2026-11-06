#if !defined INCLUDE_SKY_PLANETS
#define INCLUDE_SKY_PLANETS

#include "/include/sky/astronomy.glsl"

// Planet geometry and photometry are produced once per frame by the deferred
// compute pass; each texel pair holds (world direction, angular radius) and
// (tinted radiance, validity). The draw path is therefore seven texel fetches
// and a Gaussian, with no VSOP87 series evaluation per pixel. The image is
// read back through its sampler, the same pattern the LPV light buffers use.
uniform sampler2D planet_data_sampler;

const int ASTRO_PLANET_DRAW_COUNT = 7;

/**
 * Point-like planet discs. The physical angular radius is smaller than one
 * pixel at normal fields of view, so a minimum display radius is applied and
 * can be scaled from the Astronomy screen.
 */
vec3 astro_draw_planets(vec3 ray_dir) {
    float night_visibility = astro_night_visibility();
    if (night_visibility <= 0.0 || ray_dir.y <= 0.0) {
        return vec3(0.0);
    }

    float horizon_fade = smoothstep(0.0, 0.045, ray_dir.y);
    vec3 result = vec3(0.0);
    for (int planet = 0; planet < ASTRO_PLANET_DRAW_COUNT; ++planet) {
        vec4 geometry = texelFetch(planet_data_sampler, ivec2(2 * planet, 0), 0);
        vec4 photometry
            = texelFetch(planet_data_sampler, ivec2(2 * planet + 1, 0), 0);
        if (photometry.a <= 0.0) {
            continue;
        }

        float radius = max(
            geometry.w * ASTRO_PLANET_SIZE,
            ASTRO_PLANET_MIN_RADIUS * ASTRO_PLANET_SIZE
        );
        float separation = astro_angle_between(ray_dir, geometry.xyz);
        float normalized = separation / radius;
        // A tight core plus a faint halo: a bright planet reads as a small
        // disc instead of a single hard pixel, while faint ones stay point-like.
        float core = exp(-2.2 * normalized * normalized);
        float halo = 0.05 * exp(-0.4 * normalized * normalized);
        result += photometry.rgb * (core + halo);
    }
    return result * horizon_fade * night_visibility;
}

#endif // INCLUDE_SKY_PLANETS
