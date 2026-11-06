#if !defined INCLUDE_SKY_PLANET_STATE
#define INCLUDE_SKY_PLANET_STATE

// Only the position half of astronomy.glsl is needed here: the render half
// binds star and lunar samplers that the compute pass does not declare.
#define ASTRO_POSITION_ONLY
#include "/include/sky/astronomy.glsl"
#undef ASTRO_POSITION_ONLY
#include "/include/sky/vsop87.glsl"

// Written here once per frame; only the deferred compute pass includes this
// file. The draw side in planets.glsl declares the same image as readonly.
layout(rgba32f) writeonly uniform image2D planet_data_img;

// Planet identifiers follow Vsop87.Body in the companion; Earth is the eighth
// body and only supplies the geocentric baseline.
const int ASTRO_PLANET_MERCURY = 0;
const int ASTRO_PLANET_VENUS = 1;
const int ASTRO_PLANET_MARS = 2;
const int ASTRO_PLANET_JUPITER = 3;
const int ASTRO_PLANET_SATURN = 4;
const int ASTRO_PLANET_URANUS = 5;
const int ASTRO_PLANET_NEPTUNE = 6;
const int ASTRO_PLANET_COUNT = 7;
const int ASTRO_PLANET_EARTH = 7;

const float ASTRO_KM_PER_AU = 1.495978707e8;
// Reference magnitude that maps Jupiter to unit radiance. The star atlas
// clips around magnitude zero, so this keeps bright planets brighter than the
// brightest stars without making Venus a screen-filling glare.
const float ASTRO_PLANET_REFERENCE_MAGNITUDE = -1.0;

// Broadband disc tints.
const vec3 ASTRO_PLANET_TINT[ASTRO_PLANET_COUNT] = vec3[](
    vec3(0.80, 0.76, 0.70), // Mercury
    vec3(1.00, 0.95, 0.84), // Venus
    vec3(0.90, 0.52, 0.36), // Mars
    vec3(0.95, 0.86, 0.71), // Jupiter
    vec3(0.96, 0.88, 0.68), // Saturn
    vec3(0.66, 0.87, 0.91), // Uranus
    vec3(0.46, 0.59, 0.92)  // Neptune
);

const float ASTRO_PLANET_RADIUS_KM[ASTRO_PLANET_COUNT] = float[](
    2439.7, 6051.8, 3396.2, 71492.0, 60268.0, 25559.0, 24764.0
);

// Phase-angle polynomials from Meeus, Astronomical Algorithms, chapter 41.
// Saturn's ring term is omitted, which costs at most about one magnitude.
const float ASTRO_PLANET_MAGNITUDE_BASE[ASTRO_PLANET_COUNT] = float[](
    -0.42, -4.40, -1.52, -9.40, -8.88, -7.19, -6.87
);
const float ASTRO_PLANET_MAGNITUDE_LINEAR[ASTRO_PLANET_COUNT] = float[](
    0.0380, 0.0009, 0.0160, 0.0050, 0.0440, 0.0, 0.0
);
const float ASTRO_PLANET_MAGNITUDE_QUADRATIC[ASTRO_PLANET_COUNT] = float[](
    -0.000273, 0.000239, 0.0, 0.0, 0.0, 0.0, 0.0
);
const float ASTRO_PLANET_MAGNITUDE_CUBIC[ASTRO_PLANET_COUNT] = float[](
    0.000002, -0.00000065, 0.0, 0.0, 0.0, 0.0, 0.0
);

vec3 astro_heliocentric_rect(int body, float millennia) {
    vec3 spherical = vsop87_heliocentric(body, millennia);
    float cos_latitude = cos(spherical.y);
    return spherical.z * vec3(
        cos_latitude * cos(spherical.x),
        cos_latitude * sin(spherical.x),
        sin(spherical.y)
    );
}

// Geocentric ecliptic-of-date vector to the Minecraft world frame, using the
// same equatorial conversion and sidereal rotation as the Sun and Moon.
vec3 astro_planet_world_direction(vec3 geocentric) {
    float days = astro_ephemeris_days();
    float longitude = atan(geocentric.y, geocentric.x) * astro_rad_to_deg;
    float latitude = atan(geocentric.z, length(geocentric.xy)) * astro_rad_to_deg;
    vec3 equatorial = astro_ecliptic_to_equatorial(
        longitude, latitude, length(geocentric), days
    );
    return astro_equatorial_to_world(
        equatorial.x, equatorial.y, astro_local_sidereal_degrees()
    );
}

/**
 * Writes one geometry texel and one photometry texel per planet into
 * planet_data_img. Called once per frame from the deferred compute pass, so the
 * series cost is paid seven times per frame rather than once per pixel.
 */
void astro_store_planet_data() {
    // VSOP87 is parameterised in Julian millennia from J2000.0, while
    // astro_ephemeris_days() counts from 2000-01-00 00:00.
    float millennia = (astro_ephemeris_days() - 1.5) / 365250.0;
    vec3 earth = astro_heliocentric_rect(ASTRO_PLANET_EARTH, millennia);
    float earth_distance = length(earth);

    for (int planet = 0; planet < ASTRO_PLANET_COUNT; ++planet) {
        vec3 body = astro_heliocentric_rect(planet, millennia);
        vec3 geocentric = body - earth;
        float distance = length(geocentric);
        float heliocentric_distance = length(body);

        float cos_phase = clamp(
            (heliocentric_distance * heliocentric_distance + distance * distance
                - earth_distance * earth_distance)
                / (2.0 * heliocentric_distance * distance),
            -1.0,
            1.0
        );
        float phase_angle = acos(cos_phase) * astro_rad_to_deg;

        float magnitude = ASTRO_PLANET_MAGNITUDE_BASE[planet]
            + 5.0 * log(heliocentric_distance * distance) / log(10.0)
            + ASTRO_PLANET_MAGNITUDE_LINEAR[planet] * phase_angle
            + ASTRO_PLANET_MAGNITUDE_QUADRATIC[planet] * phase_angle * phase_angle
            + ASTRO_PLANET_MAGNITUDE_CUBIC[planet] * phase_angle * phase_angle
                * phase_angle;

        float angular_radius = asin(clamp(
            ASTRO_PLANET_RADIUS_KM[planet] / (distance * ASTRO_KM_PER_AU),
            -1.0,
            1.0
        ));

        vec3 radiance = ASTRO_PLANET_TINT[planet]
            * pow(10.0, -0.4 * (magnitude - ASTRO_PLANET_REFERENCE_MAGNITUDE))
            * ASTRO_PLANET_BRIGHTNESS;

        imageStore(planet_data_img,
            ivec2(2 * planet, 0),
            vec4(astro_planet_world_direction(geocentric), angular_radius)
        );
        imageStore(planet_data_img, ivec2(2 * planet + 1, 0), vec4(radiance, 1.0));
    }
}

#endif // INCLUDE_SKY_PLANET_STATE
