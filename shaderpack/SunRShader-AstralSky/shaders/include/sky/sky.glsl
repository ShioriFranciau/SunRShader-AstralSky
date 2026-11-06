#if !defined INCLUDE_SKY_SKY
#define INCLUDE_SKY_SKY

#include "/include/utility/color.glsl"
#include "/include/utility/dithering.glsl"
#include "/include/utility/fast_math.glsl"
#include "/include/utility/random.glsl"

//----------------------------------------------------------------------------//
#if defined WORLD_OVERWORLD

#include "/include/lighting/bsdf.glsl"
#include "/include/lighting/colors/light_color.glsl"
#include "/include/lighting/colors/weather_color.glsl"
#include "/include/misc/lightning_flash.glsl"
#include "/include/sky/atmosphere.glsl"
#include "/include/sky/projection.glsl"
#include "/include/sky/rainbow.glsl"
#ifdef ASTRO_REAL_SKY
// Include astronomy directly. Do not rely on stars.glsl to pull it in: Iris
// may preprocess the star include earlier under a different option state when
// applying settings in-game, leaving its include guard set without astronomy.
#include "/include/sky/astronomy.glsl"
#endif
#if defined ASTRO_REAL_SKY && defined ASTRO_PLANETS
#include "/include/sky/planets.glsl"
#endif
#include "/include/sky/stars.glsl"
#include "/include/utility/geometry.glsl"

#if defined PROGRAM_DEFERRED0
#include "/include/sky/clouds.glsl"

#if defined CREPUSCULAR_RAYS && !defined BLOCKY_CLOUDS
#include "/include/sky/crepuscular_rays.glsl"
#endif
#endif

const float sun_luminance = rcp(
    cone_angle_to_solid_angle(sun_angular_radius)
); // luminance of sun disk
const float moon_luminance = 10.0; // luminance of moon disk

vec3 draw_sun(vec3 ray_dir) {
    vec3 astronomical_sun_direction;
    vec3 astronomical_moon_direction;
    float lunar_distance;
    astro_celestial_directions(
        astronomical_sun_direction, astronomical_moon_direction, lunar_distance
    );
    float sun_view_angle = astro_angle_between(
        ray_dir, astronomical_sun_direction
    );
    float eclipse_coverage = astro_solar_eclipse_coverage();
    float eclipse_display_scale = astro_eclipse_display_scale(eclipse_coverage);
    float real_moon_radius = 0.2725 * astro_deg_to_rad
        * (60.2666 / lunar_distance) * eclipse_display_scale;
    float moon_view_angle = astro_angle_between(
        ray_dir, astronomical_moon_direction
    );
    float moon_edge_width = max(fwidth(moon_view_angle), 1.0e-5);
    float moon_disc = 1.0 - smoothstep(
        real_moon_radius - moon_edge_width,
        real_moon_radius + moon_edge_width,
        moon_view_angle
    );

    // Limb darkening model from
    // http://www.physics.hmc.edu/faculty/esin/a101/limbdarkening.pdf
    const vec3 alpha = vec3(0.429, 0.522, 0.614);
    float center_to_edge = max0(sun_angular_radius - sun_view_angle);
    vec3 limb_darkening
        = pow(vec3(1.0 - sqr(1.0 - center_to_edge)), 0.5 * alpha);

    float sun_edge_width = max(fwidth(sun_view_angle), 1.0e-5);
    float sun_disc = 1.0 - smoothstep(
        sun_angular_radius - sun_edge_width,
        sun_angular_radius + sun_edge_width,
        sun_view_angle
    );
    vec3 photosphere = sun_luminance * sun_color * sun_disc
        * limb_darkening * (1.0 - moon_disc);

    float angular_distance = sun_view_angle;
    float displayed_sun_radius = sun_angular_radius * eclipse_display_scale;
    float corona_profile = exp(
        -420.0 / eclipse_display_scale
            * max(angular_distance - displayed_sun_radius, 0.0)
    ) * smoothstep(
        displayed_sun_radius,
        displayed_sun_radius * 5.0,
        angular_distance
    );
    vec3 corona = sun_color * 0.18 * corona_profile
        * smoothstep(0.94, 0.995, eclipse_coverage) * (1.0 - moon_disc);
    return photosphere + corona;
}

vec4 draw_moon(vec3 ray_dir) {
    const vec3 lit_color
        = vec3(MOON_R, MOON_G <= 0.03 ? 0.0 : MOON_G - 0.03, MOON_B);
    const vec3 glow_color
        = vec3(MOON_R <= 0.05 ? 0.0 : MOON_R - 0.05, MOON_G, MOON_B);

    vec3 astronomical_sun_direction;
    vec3 astronomical_moon_direction;
    float lunar_distance;
    astro_celestial_directions(
        astronomical_sun_direction, astronomical_moon_direction, lunar_distance
    );

    // Lunar distance varies enough to change the apparent radius by about 14%.
    float real_moon_radius = 0.2725 * astro_deg_to_rad * ASTRO_MOON_SIZE
        * (60.2666 / lunar_distance);
    float moon_view_angle = astro_angle_between(
        ray_dir, astronomical_moon_direction
    );
    float moon_edge_width = max(fwidth(moon_view_angle), 1.0e-5);
    if (moon_view_angle > real_moon_radius + moon_edge_width) {
        return vec4(0.0);
    }

    float dist = clamp01(moon_view_angle / real_moon_radius);
    float moon_alpha = 1.0 - smoothstep(
        real_moon_radius - moon_edge_width,
        real_moon_radius + moon_edge_width,
        moon_view_angle
    );

    // Orient the lunar globe in its calculated IAU body frame. Lunar east is
    // positive texture longitude and the body frame naturally exposes the
    // small date-dependent optical libration around the tidally locked face.
    vec3 near_side = -astronomical_moon_direction;
    vec3 lunar_prime_meridian;
    vec3 lunar_body_east;
    vec3 lunar_body_north;
    astro_lunar_body_axes(
        lunar_prime_meridian, lunar_body_east, lunar_body_north
    );
    vec3 disc_north = normalize(
        lunar_body_north - near_side * dot(lunar_body_north, near_side)
    );
    vec3 disc_east = normalize(cross(near_side, disc_north));

    vec2 disc_position = vec2(dot(ray_dir, disc_east), dot(ray_dir, disc_north))
        / sin(real_moon_radius);

    float surface_depth = sqrt(max(1.0 - dot(disc_position, disc_position), 0.0));
    vec3 moon_normal = normalize(
        disc_east * disc_position.x + disc_north * disc_position.y
            + near_side * surface_depth
    );
    float lunar_longitude = atan(
        dot(moon_normal, lunar_body_east),
        dot(moon_normal, lunar_prime_meridian)
    );
    float lunar_latitude = asin(clamp(
        dot(moon_normal, lunar_body_north), -1.0, 1.0
    ));
    vec2 moon_uv = vec2(
        lunar_longitude / tau + 0.5,
        0.5 - lunar_latitude / pi
    );
    vec3 moon_texture = srgb_eotf_inv(texture(moon_sampler, moon_uv).rgb)
        * rec709_to_working_color;
    moon_texture = clamp(moon_texture / 0.45, vec3(0.18), vec3(1.55));
    // The Sun direction is the direction of incoming illumination at lunar
    // distance. This continuously produces crescents, quarters and gibbous
    // phases from the selected date instead of Minecraft's eight phase index.
    float moon_shadow = dampen(max0(dot(moon_normal, astronomical_sun_direction)));

    float edge_glow = sqr(sqr(sqr(dist)));

    vec3 color = max(moon_shadow * lit_color * (1.0 + 2.0 * edge_glow),
                     ASTRO_EARTHSHINE * glow_color * (0.5 + 0.5 * edge_glow))
        * mix(vec3(dot(moon_texture, luminance_weights_rec709)), moon_texture, 0.32);
    color = moon_luminance * sqr(color);
    return vec4(color, moon_alpha);
}

#ifdef GALAXY
vec3 draw_galaxy(vec3 ray_dir, out float galaxy_luminance) {
#ifdef ASTRO_REAL_SKY
    vec3 galaxy = astro_draw_milky_way(ray_dir);
    galaxy_luminance = dot(galaxy, luminance_weights_rec709);
    return galaxy;
#else
    const vec3 galaxy_tint = vec3(0.75, 0.66, 1.0) * GALAXY_INTENSITY;

    float galaxy_intensity = 0.05 + 1.0 * linear_step(-0.1, 0.25, -sun_dir.y);

    float lon = atan(ray_dir.x, ray_dir.z);
    float lat = fast_acos(-ray_dir.y);

    vec3 galaxy
        = texture(galaxy_sampler, vec2(lon * rcp(tau) + 0.5, lat * rcp(pi)))
              .rgb;

    galaxy = srgb_eotf_inv(galaxy) * rec709_to_working_color;

    galaxy *= galaxy_intensity * galaxy_tint;

    galaxy_luminance = dot(galaxy, luminance_weights_rec709);

    galaxy = mix(vec3(galaxy_luminance), galaxy, 2.0);

    return max0(galaxy);
#endif
}
#endif

vec3 draw_sky(
    vec3 ray_dir,
    vec3 atmosphere,
    vec4 clouds_and_aurora,
    float clouds_apparent_distance
) {
    vec3 sky = vec3(0.0);

#if defined SHADOW
    // Trick to make stars rotate with sun and moon
    mat3 rot = (sunAngle < 0.5)
        ? mat3(shadowModelViewInverse)
        : mat3(
              -shadowModelViewInverse[0].xyz,
              shadowModelViewInverse[1].xyz,
              -shadowModelViewInverse[2].xyz
          );

    vec3 celestial_dir = ray_dir * rot;
#else
    vec3 celestial_dir = ray_dir;
#endif

    // Galaxy

#ifdef GALAXY
    float galaxy_luminance;
#ifdef ASTRO_REAL_SKY
    sky += draw_galaxy(ray_dir, galaxy_luminance);
#else
    sky += draw_galaxy(celestial_dir, galaxy_luminance);
#endif
#else
    const float galaxy_luminance = 0.0;
#endif

    // Sun, moon stars

#if defined PROGRAM_DEFERRED4
    vec3 skytextured_output
        = texelFetch(colortex0, ivec2(gl_FragCoord.xy), 0).rgb;
    sky += texelFetch(colortex0, ivec2(gl_FragCoord.xy), 0).rgb;

#ifdef STARS
    // Stars
    float stars_visibility
        = clamp01(1.0 - dot(skytextured_output, vec3(0.33) * 256.0));
#ifdef ASTRO_REAL_SKY
    sky += draw_stars(ray_dir, galaxy_luminance) * stars_visibility;
#else
    sky += draw_stars(celestial_dir, galaxy_luminance) * stars_visibility;
#endif
#endif

#ifdef ASTRO_PLANETS
    // Planets: VSOP87 directions computed once per frame by the deferred
    // compute pass, composited exactly like the star field so atmosphere,
    // clouds and rain act on them.
    sky += astro_draw_planets(ray_dir);
#endif

#ifndef VANILLA_SUN
    // Shader sun
    sky += draw_sun(ray_dir);
#endif

#ifndef VANILLA_MOON
    // Shader moon
    vec4 moon = draw_moon(ray_dir);
    sky *= 1.0 - moon.a;
    sky += moon.rgb;
#endif
#endif

    // Atmosphere

    sky *= atmosphere_transmittance(ray_dir.y, planet_radius)
        * (1.0 - rainStrength);
    sky += atmosphere;

#ifdef ASTRO_REAL_SKY
    // Atmosphere is composited after the Sun and Moon. Reapply the physical
    // topocentric lunar silhouette here during a solar eclipse so forward
    // scattering cannot fill the black lunar disc back in. Clouds remain
    // later in the pipeline and can still occlude the entire event.
    float eclipse_coverage = astro_solar_eclipse_coverage();
    if (eclipse_coverage > 0.0) {
        vec3 eclipse_sun_direction;
        vec3 eclipse_moon_direction;
        float eclipse_lunar_distance;
        astro_celestial_directions(
            eclipse_sun_direction,
            eclipse_moon_direction,
            eclipse_lunar_distance
        );
        float eclipse_moon_radius = 0.2725 * astro_deg_to_rad
            * (60.2666 / eclipse_lunar_distance)
            * astro_eclipse_display_scale(eclipse_coverage);
        float eclipse_moon_angle = astro_angle_between(
            ray_dir, eclipse_moon_direction
        );
        float silhouette_edge_width = max(fwidth(eclipse_moon_angle), 1.0e-5);
        float lunar_silhouette = 1.0 - smoothstep(
            eclipse_moon_radius - silhouette_edge_width,
            eclipse_moon_radius + silhouette_edge_width,
            eclipse_moon_angle
        );
        sky *= 1.0 - lunar_silhouette
            * smoothstep(0.001, 0.02, eclipse_coverage);
    }
#endif

    // Clouds, aurora, crepuscular rays

    sky *= clouds_and_aurora.a; // Transmittance
    sky += clouds_and_aurora.rgb; // Scattering

    // Rainbow

    sky = draw_rainbows(
        sky,
        ray_dir,
        mix(clouds_apparent_distance,
            1e6,
            linear_step(1.0, 0.95, clouds_and_aurora.w))
    );

    // Cave sky fix

#if !defined PROGRAM_DEFERRED0
    // Fade lower part of sky into cave fog color when underground so that the
    // sky isn't visible beyond the render distance
    float underground_sky_fade
        = biome_cave * smoothstep(-0.1, 0.1, 0.4 - ray_dir.y);
    sky = mix(sky, vec3(0.0), underground_sky_fade);
#endif

    return sky;
}

#if defined PROGRAM_DEFERRED0
vec4 get_clouds_and_aurora(
    vec3 ray_dir,
    vec3 clear_sky,
    out float clouds_apparent_distance
) {
    clouds_apparent_distance = 1e6;

    ivec2 texel = ivec2(gl_FragCoord.xy);
    texel.x = texel.x % (sky_map_res.x - 4);

    float dither = interleaved_gradient_noise(vec2(texel));

    // Clouds

#ifndef BLOCKY_CLOUDS
    const vec3 air_viewer_pos = vec3(0.0, planet_radius, 0.0);
    CloudsResult result
        = draw_clouds(air_viewer_pos, ray_dir, clear_sky, -1.0, dither);

    // Lightning flash
    result.scattering.rgb += LIGHTNING_FLASH_UNIFORM * lightning_flash_intensity
        * result.scattering.a;
#else
    CloudsResult result = clouds_not_hit;
#endif

    clouds_apparent_distance = result.apparent_distance;

    // Aurora

    vec3 aurora = draw_aurora(ray_dir, dither);

    vec4 clouds_and_aurora = vec4(
        result.scattering.xyz + aurora * result.transmittance,
        result.transmittance
    );

    // Crepuscular rays

#if defined CREPUSCULAR_RAYS && !defined BLOCKY_CLOUDS
    vec4 crepuscular_rays
        = draw_crepuscular_rays(colortex8, ray_dir, false, 0.5);
    clouds_and_aurora *= crepuscular_rays.w;
    clouds_and_aurora.rgb += crepuscular_rays.xyz;
#endif

    return clouds_and_aurora;
}

vec3 draw_sky(vec3 ray_dir) {
    vec3 atmospheric_sun_direction = sun_dir;
    vec3 atmospheric_moon_direction = moon_dir;
#ifdef ASTRO_REAL_SKY
    float lunar_distance;
    astro_celestial_directions(
        atmospheric_sun_direction, atmospheric_moon_direction, lunar_distance
    );
#endif
    vec3 atmosphere = atmosphere_scattering(
        ray_dir,
        sun_color,
        atmospheric_sun_direction,
        moon_color,
        atmospheric_moon_direction,
        true
    );
    float clouds_apparent_distance;
    vec4 clouds_and_aurora
        = get_clouds_and_aurora(ray_dir, atmosphere, clouds_apparent_distance);
    return draw_sky(
        ray_dir,
        atmosphere,
        clouds_and_aurora,
        clouds_apparent_distance
    );
}
#endif

//----------------------------------------------------------------------------//
#elif defined WORLD_NETHER

vec3 draw_sky(vec3 ray_dir) { return ambient_color; }

//----------------------------------------------------------------------------//
#elif defined WORLD_END

#include "/include/misc/end_lighting_fix.glsl"
#include "/include/sky/atmosphere.glsl"
#include "/include/sky/stars.glsl"

const float sun_solid_angle = cone_angle_to_solid_angle(sun_angular_radius);
const vec3 end_sun_color = vec3(1.0, 0.5, 0.25);

vec3 draw_sun(vec3 ray_dir) {
    float nu = dot(ray_dir, sun_dir);
    float r = fast_acos(nu);

    // Sun disk

    const vec3 alpha = vec3(0.6, 0.5, 0.4);
    float center_to_edge = max0(sun_angular_radius - r);
    vec3 limb_darkening
        = pow(vec3(1.0 - sqr(1.0 - center_to_edge)), 0.5 * alpha);
    vec3 sun_disk = vec3(r < sun_angular_radius);

    // Solar flare effect

    // Transform the coordinate space such that z is parallel to sun_dir
    vec3 tangent = sun_dir.y == 1.0
        ? vec3(1.0, 0.0, 0.0)
        : normalize(cross(vec3(0.0, 1.0, 0.0), sun_dir));
    vec3 bitangent = normalize(cross(tangent, sun_dir));
    mat3 rot = mat3(tangent, bitangent, sun_dir);

    // Vector from ray dir to sun dir
    vec2 q = ((ray_dir - sun_dir) * rot).xy;

    float theta = fract(
        linear_step(-pi, pi, atan(q.y, q.x)) + 0.015 * frameTimeCounter
        - 0.33 * r
    );

    float flare
        = texture(noisetex, vec2(theta, r - 0.025 * frameTimeCounter)).x;
    flare = pow5(flare) * exp(-25.0 * (r - sun_angular_radius));
    flare = r < sun_angular_radius ? 0.0 : flare;

    return end_sun_color * rcp(sun_solid_angle) * max0(sun_disk + 0.1 * flare);
}

vec3 draw_sky(vec3 ray_dir) {
    // Sky gradient

    float up_gradient
        = linear_step(0.0, 0.4, ray_dir.y) + linear_step(0.1, 0.8, -ray_dir.y);
    vec3 sky = ambient_color * mix(0.1, 0.04, up_gradient);
    float mie_phase = cornette_shanks_phase(dot(ray_dir, sun_dir), 0.6);
    sky += 0.1 * (ambient_color + 0.5 * end_sun_color) * mie_phase;

#if defined PROGRAM_DEFERRED4
    // Sun

#ifdef END_SUN_EFFECT
    sky += draw_sun(ray_dir);
#endif

    // Stars

    vec3 stars_fade
        = exp2(-0.1 * max0(1.0 - ray_dir.y) / max(ambient_color, eps))
        * linear_step(-0.2, 0.0, ray_dir.y);
    sky += draw_stars(ray_dir, 0.0).xzy * stars_fade;
#endif

    return sky;
}

#endif

#endif // INCLUDE_SKY_SKY
