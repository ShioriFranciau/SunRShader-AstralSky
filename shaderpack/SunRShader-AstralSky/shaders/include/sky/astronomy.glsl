#if !defined INCLUDE_SKY_ASTRONOMY_POSITION
#define INCLUDE_SKY_ASTRONOMY_POSITION

#include "/lib/astro_config.glsl"

// Declared here because the observation time modes are shared by every shader
// stage that evaluates astronomical positions.
uniform int astro_world_time;
uniform int astro_world_day;

// Iris' OS date and wall-clock uniforms. Declared beside the observation-time
// code: the real-time mode reads them from every stage that positions the
// sky, while the fixed modes keep the branch dead at preprocessing.
uniform ivec3 currentDate;
uniform ivec3 currentTime;

const float astro_deg_to_rad = pi / 180.0;
const float astro_rad_to_deg = 180.0 / pi;

vec3 astro_equatorial_to_world(
    float right_ascension_degrees,
    float declination_degrees,
    float sidereal_degrees
);

float astro_positive_modulo(float value, float divisor) {
    return value - divisor * floor(value / divisor);
}

float astro_angle_between(vec3 first, vec3 second) {
    // acos(dot) loses most of its precision for the sub-degree separation
    // encountered during an eclipse. atan(|cross|, dot) remains well
    // conditioned as the angle approaches zero and prevents frame-to-frame
    // coverage/exposure jumps on 32-bit shader floats.
    return atan(
        length(cross(first, second)),
        clamp(dot(first, second), -1.0, 1.0)
    );
}

void astro_observation_time(
    out int year,
    out int month,
    out int day,
    out float utc_hours
) {
#if ASTRO_TIME_MODE == 0
    year = currentDate.x;
    month = currentDate.y;
    day = currentDate.z;
    utc_hours = float(currentTime.x) + float(currentTime.y) / 60.0
        + float(currentTime.z) / 3600.0 - ASTRO_UTC_OFFSET;
#elif ASTRO_TIME_MODE == 1
    year = ASTRO_FIXED_YEAR;
    month = ASTRO_FIXED_MONTH;
    day = ASTRO_FIXED_DAY;
    utc_hours = ASTRO_FIXED_HOUR - ASTRO_UTC_OFFSET;
#elif ASTRO_TIME_MODE == 2
    year = ASTRO_FIXED_YEAR;
    month = ASTRO_FIXED_MONTH;
    day = ASTRO_FIXED_DAY;
    int elapsed_world_days = astro_world_day - ASTRO_ADVANCING_START_DAY;
    int elapsed_world_ticks = astro_world_time - ASTRO_ADVANCING_START_TIME;
    // The companion initializes the game clock to the configured local hour
    // (tick 0 = 06:00) and captures that instant as the anchor, so the anchor
    // hour mirrors the game clock. Civil time is converted with the
    // configured UTC offset, never as apparent solar time.
    float anchor_local_hour = astro_positive_modulo(
        float(ASTRO_ADVANCING_START_TIME) / 1000.0 + 6.0, 24.0
    );
    // utc_hours is intentionally allowed outside [0, 24);
    // astro_days_since_j2000 carries whole days.
    utc_hours = anchor_local_hour - ASTRO_UTC_OFFSET
        + 24.0 * float(elapsed_world_days)
        + float(elapsed_world_ticks) / 1000.0;
#else
    // Defensive fallback for a stale runtime configuration: system real time.
    year = currentDate.x;
    month = currentDate.y;
    day = currentDate.z;
    utc_hours = float(currentTime.x) + float(currentTime.y) / 60.0
        + float(currentTime.z) / 3600.0 - ASTRO_UTC_OFFSET;
#endif
}

void astro_days_since_j2000(
    int year,
    int month,
    int day,
    float utc_hours,
    out int whole_days,
    out float fractional_day
) {
    // The Julian day stays in integer form until the final sub-day term. This
    // avoids losing minutes of precision on GPUs whose float is only 32 bits.
    int a = (14 - month) / 12;
    int y = year + 4800 - a;
    int m = month + 12 * a - 3;
    int julian_day_number = day + (153 * m + 2) / 5 + 365 * y
        + y / 4 - y / 100 + y / 400 - 32045;

    whole_days = julian_day_number - 2451545;
    fractional_day = (utc_hours - 12.0) / 24.0;
    int day_carry = int(floor(fractional_day));
    whole_days += day_carry;
    fractional_day -= float(day_carry);
}

float astro_local_sidereal_degrees() {
    int year;
    int month;
    int day;
    float utc_hours;
    astro_observation_time(year, month, day, utc_hours);

    int whole_days;
    float fractional_day;
    astro_days_since_j2000(
        year, month, day, utc_hours, whole_days, fractional_day
    );

    float gmst = 280.46061837
        + 0.98564736629 * float(whole_days)
        + 360.98564736629 * fractional_day;
    return astro_positive_modulo(gmst + ASTRO_LONGITUDE, 360.0);
}

float astro_ephemeris_days() {
    int year;
    int month;
    int day;
    float utc_hours;
    astro_observation_time(year, month, day, utc_hours);

    int whole_days;
    float fractional_day;
    astro_days_since_j2000(
        year, month, day, utc_hours, whole_days, fractional_day
    );
    // The compact solar/lunar elements below use 2000-01-00 00:00 UTC.
    // astro_days_since_j2000 is relative to 2000-01-01 12:00 UTC, so
    // this epoch is 1.5 days earlier (not one day earlier).
    return float(whole_days) + fractional_day + 1.5;
}

float astro_eccentric_anomaly(float mean_anomaly_degrees, float eccentricity) {
    float estimate = mean_anomaly_degrees + astro_rad_to_deg * eccentricity
        * sin(mean_anomaly_degrees * astro_deg_to_rad)
        * (1.0 + eccentricity * cos(mean_anomaly_degrees * astro_deg_to_rad));
    for (int iteration = 0; iteration < 3; ++iteration) {
        float radians = estimate * astro_deg_to_rad;
        float error = estimate - astro_rad_to_deg * eccentricity * sin(radians)
            - mean_anomaly_degrees;
        estimate -= error / (1.0 - eccentricity * cos(radians));
    }
    return estimate;
}

vec3 astro_ecliptic_to_equatorial(
    float longitude_degrees,
    float latitude_degrees,
    float distance,
    float days
) {
    float longitude = longitude_degrees * astro_deg_to_rad;
    float latitude = latitude_degrees * astro_deg_to_rad;
    float obliquity = (23.4393 - 3.563e-7 * days) * astro_deg_to_rad;
    vec3 ecliptic = distance * vec3(
        cos(longitude) * cos(latitude),
        sin(longitude) * cos(latitude),
        sin(latitude)
    );
    vec3 equatorial = vec3(
        ecliptic.x,
        ecliptic.y * cos(obliquity) - ecliptic.z * sin(obliquity),
        ecliptic.y * sin(obliquity) + ecliptic.z * cos(obliquity)
    );
    return vec3(
        astro_positive_modulo(atan(equatorial.y, equatorial.x) * astro_rad_to_deg, 360.0),
        atan(equatorial.z, length(equatorial.xy)) * astro_rad_to_deg,
        distance
    );
}

vec3 astro_solar_equatorial(float days) {
    float perihelion = astro_positive_modulo(282.9404 + 4.70935e-5 * days, 360.0);
    float eccentricity = 0.016709 - 1.151e-9 * days;
    float mean_anomaly = astro_positive_modulo(356.0470 + 0.9856002585 * days, 360.0);
    float eccentric_anomaly = astro_eccentric_anomaly(mean_anomaly, eccentricity)
        * astro_deg_to_rad;
    vec2 orbital = vec2(
        cos(eccentric_anomaly) - eccentricity,
        sqrt(1.0 - eccentricity * eccentricity) * sin(eccentric_anomaly)
    );
    return astro_ecliptic_to_equatorial(
        astro_positive_modulo(atan(orbital.y, orbital.x) * astro_rad_to_deg + perihelion, 360.0),
        0.0,
        length(orbital),
        days
    );
}

vec3 astro_lunar_equatorial(float days) {
    float node = astro_positive_modulo(125.1228 - 0.0529538083 * days, 360.0);
    float periapsis = astro_positive_modulo(318.0634 + 0.1643573223 * days, 360.0);
    float mean_anomaly = astro_positive_modulo(115.3654 + 13.0649929509 * days, 360.0);
    const float eccentricity = 0.0549;
    float eccentric_anomaly = astro_eccentric_anomaly(mean_anomaly, eccentricity)
        * astro_deg_to_rad;
    vec2 orbital = vec2(
        cos(eccentric_anomaly) - eccentricity,
        sqrt(1.0 - eccentricity * eccentricity) * sin(eccentric_anomaly)
    );
    float true_anomaly = atan(orbital.y, orbital.x) * astro_rad_to_deg;
    float distance = 60.2666 * length(orbital);
    float argument = (true_anomaly + periapsis) * astro_deg_to_rad;
    float node_radians = node * astro_deg_to_rad;
    const float inclination = 5.1454 * astro_deg_to_rad;
    vec3 ecliptic = distance * vec3(
        cos(node_radians) * cos(argument)
            - sin(node_radians) * sin(argument) * cos(inclination),
        sin(node_radians) * cos(argument)
            + cos(node_radians) * sin(argument) * cos(inclination),
        sin(argument) * sin(inclination)
    );
    float longitude = atan(ecliptic.y, ecliptic.x) * astro_rad_to_deg;
    float latitude = atan(ecliptic.z, length(ecliptic.xy)) * astro_rad_to_deg;
    float solar_mean_anomaly = astro_positive_modulo(356.0470 + 0.9856002585 * days, 360.0);
    float solar_mean_longitude = astro_positive_modulo(
        282.9404 + 4.70935e-5 * days + solar_mean_anomaly, 360.0
    );
    float lunar_mean_longitude = astro_positive_modulo(node + periapsis + mean_anomaly, 360.0);
    float elongation = astro_positive_modulo(lunar_mean_longitude - solar_mean_longitude, 360.0);
    float argument_of_latitude = astro_positive_modulo(lunar_mean_longitude - node, 360.0);
#define ASTRO_SIN_DEG(value) sin((value) * astro_deg_to_rad)
#define ASTRO_COS_DEG(value) cos((value) * astro_deg_to_rad)
    longitude += -1.274 * ASTRO_SIN_DEG(mean_anomaly - 2.0 * elongation)
        + 0.658 * ASTRO_SIN_DEG(2.0 * elongation)
        - 0.186 * ASTRO_SIN_DEG(solar_mean_anomaly)
        - 0.059 * ASTRO_SIN_DEG(2.0 * mean_anomaly - 2.0 * elongation)
        - 0.057 * ASTRO_SIN_DEG(mean_anomaly - 2.0 * elongation + solar_mean_anomaly)
        + 0.053 * ASTRO_SIN_DEG(mean_anomaly + 2.0 * elongation)
        + 0.046 * ASTRO_SIN_DEG(2.0 * elongation - solar_mean_anomaly)
        + 0.041 * ASTRO_SIN_DEG(mean_anomaly - solar_mean_anomaly)
        - 0.035 * ASTRO_SIN_DEG(elongation)
        - 0.031 * ASTRO_SIN_DEG(mean_anomaly + solar_mean_anomaly)
        - 0.015 * ASTRO_SIN_DEG(2.0 * argument_of_latitude - 2.0 * elongation)
        + 0.011 * ASTRO_SIN_DEG(mean_anomaly - 4.0 * elongation);
    latitude += -0.173 * ASTRO_SIN_DEG(argument_of_latitude - 2.0 * elongation)
        - 0.055 * ASTRO_SIN_DEG(mean_anomaly - argument_of_latitude - 2.0 * elongation)
        - 0.046 * ASTRO_SIN_DEG(mean_anomaly + argument_of_latitude - 2.0 * elongation)
        + 0.033 * ASTRO_SIN_DEG(argument_of_latitude + 2.0 * elongation)
        + 0.017 * ASTRO_SIN_DEG(2.0 * mean_anomaly + argument_of_latitude);
    distance += -0.58 * ASTRO_COS_DEG(mean_anomaly - 2.0 * elongation)
        - 0.46 * ASTRO_COS_DEG(2.0 * elongation);
#undef ASTRO_SIN_DEG
#undef ASTRO_COS_DEG
    return astro_ecliptic_to_equatorial(
        astro_positive_modulo(longitude, 360.0), latitude, distance, days
    );
}

void astro_celestial_directions(
    out vec3 astronomical_sun_direction,
    out vec3 astronomical_moon_direction,
    out float lunar_distance
) {
    float days = astro_ephemeris_days();
    float sidereal_degrees = astro_local_sidereal_degrees();
    vec3 solar = astro_solar_equatorial(days);
    vec3 lunar = astro_lunar_equatorial(days);
    astronomical_sun_direction = astro_equatorial_to_world(
        solar.x, solar.y, sidereal_degrees
    );
    vec3 geocentric_moon_direction = astro_equatorial_to_world(
        lunar.x, lunar.y, sidereal_degrees
    );
    // The Moon is close enough that the observer's position on Earth shifts
    // it by as much as one degree. This topocentric correction is essential
    // for placing a solar eclipse on the correct ground track. Distances are
    // in Earth radii and the local +Y axis is the observer's radial direction.
    vec3 topocentric_moon = geocentric_moon_direction * lunar.z
        - vec3(0.0, 1.0, 0.0);
    lunar_distance = length(topocentric_moon);
    astronomical_moon_direction = topocentric_moon / lunar_distance;
}

void astro_lunar_body_axes(
    out vec3 lunar_prime_meridian,
    out vec3 lunar_east,
    out vec3 lunar_north
) {
    // IAU mean lunar orientation. Together the pole and prime-meridian angle
    // rotate the LRO longitude/latitude map in inertial space. Comparing this
    // body frame with the calculated Earth-Moon line also produces optical
    // libration instead of pinning the texture centre to the apparent disc.
    float days = astro_ephemeris_days() - 1.5;
    float centuries = days / 36525.0;
    float pole_ra = (269.9949 + 0.0031 * centuries) * astro_deg_to_rad;
    float pole_dec = (66.5392 + 0.0130 * centuries) * astro_deg_to_rad;
    float prime_meridian = astro_positive_modulo(
        38.3213 + 13.17635815 * days - 1.4e-12 * days * days,
        360.0
    ) * astro_deg_to_rad;

    vec3 equatorial_north = vec3(
        cos(pole_dec) * cos(pole_ra),
        cos(pole_dec) * sin(pole_ra),
        sin(pole_dec)
    );
    vec3 equatorial_node = vec3(-sin(pole_ra), cos(pole_ra), 0.0);
    vec3 equatorial_quadrature = cross(equatorial_north, equatorial_node);
    vec3 equatorial_prime = cos(prime_meridian) * equatorial_node
        + sin(prime_meridian) * equatorial_quadrature;
    vec3 equatorial_east = -sin(prime_meridian) * equatorial_node
        + cos(prime_meridian) * equatorial_quadrature;

    float sidereal = astro_local_sidereal_degrees();
    vec3 equatorial_x = astro_equatorial_to_world(0.0, 0.0, sidereal);
    vec3 equatorial_y = astro_equatorial_to_world(90.0, 0.0, sidereal);
    vec3 equatorial_z = astro_equatorial_to_world(0.0, 90.0, sidereal);
    mat3 equatorial_to_world = mat3(
        equatorial_x, equatorial_y, equatorial_z
    );
    lunar_prime_meridian = normalize(equatorial_to_world * equatorial_prime);
    lunar_east = normalize(equatorial_to_world * equatorial_east);
    lunar_north = normalize(equatorial_to_world * equatorial_north);
}

float astro_solar_eclipse_coverage() {
    vec3 sun_direction;
    vec3 moon_direction;
    float lunar_distance;
    astro_celestial_directions(sun_direction, moon_direction, lunar_distance);

    const float sun_radius = 0.2666 * astro_deg_to_rad;
    float moon_radius = 0.2725 * astro_deg_to_rad * (60.2666 / lunar_distance);
    float separation = astro_angle_between(sun_direction, moon_direction);

    if (separation >= sun_radius + moon_radius) return 0.0;
    if (separation <= abs(moon_radius - sun_radius)) {
        float smaller_radius = min(sun_radius, moon_radius);
        return clamp(
            smaller_radius * smaller_radius / (sun_radius * sun_radius),
            0.0,
            1.0
        );
    }

    float separation_squared = separation * separation;
    float sun_squared = sun_radius * sun_radius;
    float moon_squared = moon_radius * moon_radius;
    float sun_segment = acos(clamp(
        (separation_squared + sun_squared - moon_squared)
            / (2.0 * separation * sun_radius),
        -1.0,
        1.0
    ));
    float moon_segment = acos(clamp(
        (separation_squared + moon_squared - sun_squared)
            / (2.0 * separation * moon_radius),
        -1.0,
        1.0
    ));
    float triangle = 0.5 * sqrt(max(
        (-separation + sun_radius + moon_radius)
        * (separation + sun_radius - moon_radius)
        * (separation - sun_radius + moon_radius)
        * (separation + sun_radius + moon_radius),
        0.0
    ));
    float overlap = sun_squared * sun_segment
        + moon_squared * moon_segment - triangle;
    return clamp(overlap / (pi * sun_squared), 0.0, 1.0);
}

float astro_eclipse_light_transmission() {
    // The small floor represents coronal light at totality and prevents the
    // exposure pipeline from collapsing to exactly black.
    return mix(1.0, 0.002, astro_solar_eclipse_coverage());
}

float astro_eclipse_display_scale(float coverage) {
    // A physical solar disc is only a few pixels wide at normal Minecraft
    // FOV. Enlarge the coupled Sun/Moon/corona presentation only very close
    // to totality so bloom cannot erase the black lunar silhouette. Eclipse
    // timing, position, coverage and lighting remain physically calculated.
    float clamped_coverage = clamp(coverage, 0.0, 1.0);
    float coverage_squared = clamped_coverage * clamped_coverage;
    float gradual_totality = coverage_squared * coverage_squared;
    return mix(1.0, 4.0, gradual_totality);
}

vec3 astro_equatorial_to_world(
    float right_ascension_degrees,
    float declination_degrees,
    float sidereal_degrees
) {
    float hour_angle
        = (sidereal_degrees - right_ascension_degrees) * astro_deg_to_rad;
    float declination = declination_degrees * astro_deg_to_rad;
    float latitude = ASTRO_LATITUDE * astro_deg_to_rad;

    float cos_declination = cos(declination);
    float east = -cos_declination * sin(hour_angle);
    float north = sin(declination) * cos(latitude)
        - cos_declination * cos(hour_angle) * sin(latitude);
    float up = sin(declination) * sin(latitude)
        + cos_declination * cos(hour_angle) * cos(latitude);

    float north_offset = ASTRO_NORTH_OFFSET * astro_deg_to_rad;
    float rotated_east = east * cos(north_offset) - north * sin(north_offset);
    float rotated_north = east * sin(north_offset) + north * cos(north_offset);

    // Minecraft world frame: +X east, +Y up, -Z north.
    return normalize(vec3(rotated_east, up, -rotated_north));
}

vec3 astro_world_to_equatorial(vec3 world_direction) {
    float sidereal_degrees = astro_local_sidereal_degrees();
    vec3 equatorial_x = astro_equatorial_to_world(0.0, 0.0, sidereal_degrees);
    vec3 equatorial_y = astro_equatorial_to_world(90.0, 0.0, sidereal_degrees);
    vec3 equatorial_z = astro_equatorial_to_world(0.0, 90.0, sidereal_degrees);
    return normalize(vec3(
        dot(world_direction, equatorial_x),
        dot(world_direction, equatorial_y),
        dot(world_direction, equatorial_z)
    ));
}

#endif // INCLUDE_SKY_ASTRONOMY_POSITION

#if !defined ASTRO_POSITION_ONLY && !defined INCLUDE_SKY_ASTRONOMY_RENDERING
#define INCLUDE_SKY_ASTRONOMY_RENDERING

vec2 astro_equatorial_uv(vec3 equatorial_direction) {
    float right_ascension = astro_positive_modulo(
        atan(equatorial_direction.y, equatorial_direction.x)
            * astro_rad_to_deg,
        360.0
    );
    float declination = asin(clamp(equatorial_direction.z, -1.0, 1.0));
    return vec2(right_ascension / 360.0, declination / pi + 0.5);
}

float astro_night_visibility() {
    vec3 astronomical_sun_direction;
    vec3 astronomical_moon_direction;
    float lunar_distance;
    astro_celestial_directions(
        astronomical_sun_direction, astronomical_moon_direction, lunar_distance
    );
    return smoothstep(-0.20, 0.05, -astronomical_sun_direction.y)
        * (1.0 - 0.9 * clamp(rainStrength, 0.0, 1.0));
}

vec3 astro_sample_point_stars(vec2 catalogue_uv, float angular_pixel) {
    ivec2 atlas_size = textureSize(galaxy_sampler, 0);
    vec2 atlas_position = catalogue_uv * vec2(atlas_size) - 0.5;
    ivec2 atlas_base = ivec2(floor(atlas_position));
    float declination_scale = max(cos((catalogue_uv.y - 0.5) * pi), 0.0001);
    angular_pixel = max(angular_pixel, 0.000001);
    vec3 point_catalogue = vec3(0.0);

    for (int offset_y = -1; offset_y <= 1; offset_y++) {
        for (int offset_x = -1; offset_x <= 1; offset_x++) {
            ivec2 point_texel = atlas_base + ivec2(offset_x, offset_y);
            point_texel.x = (point_texel.x % atlas_size.x + atlas_size.x) % atlas_size.x;
            point_texel.y = clamp(point_texel.y, 0, atlas_size.y - 1);
            vec4 point = texelFetch(galaxy_sampler, point_texel, 0);
            if (point.a < 0.5) {
                continue;
            }

            vec2 point_uv = (vec2(point_texel) + 0.5) / vec2(atlas_size);
            float delta_ra = fract(point_uv.x - catalogue_uv.x + 0.5) - 0.5;
            vec2 angular_delta = vec2(
                delta_ra * tau * declination_scale,
                (point_uv.y - catalogue_uv.y) * pi
            );
            float pixel_distance = length(angular_delta) / angular_pixel;
            float point_shape = exp2(-1.6 * pixel_distance * pixel_distance);
            point_catalogue = max(point_catalogue, point.rgb * point_shape);
        }
    }
    return point_catalogue;
}

vec3 astro_draw_catalog_stars(vec3 world_direction) {
    if (world_direction.y <= 0.0) {
        return vec3(0.0);
    }

    vec3 equatorial_direction = astro_world_to_equatorial(world_direction);
    vec2 catalogue_uv = astro_equatorial_uv(equatorial_direction);
    vec3 catalogue = texture(galaxy_sampler, catalogue_uv).rgb;
    float angular_pixel = max(
        length(dFdx(equatorial_direction)),
        length(dFdy(equatorial_direction))
    );
    float atlas_texels_per_pixel = angular_pixel
        * float(textureSize(galaxy_sampler, 0).x) / tau;
    float point_source_mix = 1.0 - smoothstep(0.16, 0.34, atlas_texels_per_pixel);
    if (point_source_mix > 0.0) {
        vec3 point_catalogue = astro_sample_point_stars(
            catalogue_uv, angular_pixel
        );
        catalogue = mix(catalogue, point_catalogue * 1.18, point_source_mix);
    }
    catalogue = srgb_eotf_inv(catalogue) * rec709_to_working_color;
    float catalogue_luminance = dot(catalogue, luminance_weights_rec709);
    catalogue = mix(vec3(catalogue_luminance), catalogue, ASTRO_STAR_COLOR);
    float faint_exponent = mix(
        2.2, 0.72, clamp(ASTRO_FAINT_STAR_STRENGTH, 0.0, 1.25) / 1.25
    );
    catalogue = pow(max(catalogue, vec3(0.0)), vec3(faint_exponent));

    float horizon_fade = smoothstep(0.0, 0.045, world_direction.y);
    float twinkle_phase = dot(floor(catalogue_uv
        * vec2(textureSize(galaxy_sampler, 0))),
        vec2(0.06711056, 0.00583715));
    float horizon_twinkle = mix(1.8, 0.45, clamp(world_direction.y, 0.0, 1.0));
    float twinkle = 1.0 + sin(frameTimeCounter * 2.7 + twinkle_phase)
        * ASTRO_TWINKLE * horizon_twinkle;

    return catalogue * horizon_fade * max(twinkle, 0.0)
        * ASTRO_CATALOG_BRIGHTNESS * astro_night_visibility();
}

vec3 astro_equatorial_to_galactic(vec3 equatorial_direction) {
    return normalize(vec3(
        dot(vec3(-0.0548755604, -0.8734370902, -0.4838350155), equatorial_direction),
        dot(vec3( 0.4941094279, -0.4448296300,  0.7469822445), equatorial_direction),
        dot(vec3(-0.8676661490, -0.1980763734,  0.4559837762), equatorial_direction)
    ));
}

vec3 astro_draw_milky_way(vec3 world_direction) {
    if (world_direction.y <= 0.0 || ASTRO_MILKY_WAY_BRIGHTNESS <= 0.0) {
        return vec3(0.0);
    }

    vec3 galactic_direction
        = astro_equatorial_to_galactic(astro_world_to_equatorial(world_direction));
    float longitude = atan(galactic_direction.y, galactic_direction.x);
    float latitude = asin(clamp(galactic_direction.z, -1.0, 1.0));
    float absolute_latitude = abs(latitude);

    float warp = 0.018 * sin(longitude * 2.0 - 0.7)
        + 0.008 * sin(longitude * 5.0 + 1.1);
    float warped_latitude = latitude - warp;
    float thin_disc = exp(-pow(abs(warped_latitude) / 0.095, 1.20));
    float broad_disc = 0.42 * exp(-pow(absolute_latitude / 0.27, 1.35));
    float bulge = 1.10 * exp(-sqr(longitude / 0.52) - sqr(latitude / 0.22));
    float central_lane = exp(-sqr((warped_latitude + 0.006 * sin(longitude * 11.0)) / 0.026));
    float dust_strength = mix(0.22, 0.52, exp(-sqr(longitude / 1.20)));
    float dust_transmission = 1.0 - central_lane * dust_strength;
    float structure = 0.82
        + 0.10 * sin(longitude * 5.0 + sin(longitude * 2.0) * 1.4 + latitude * 17.0)
        + 0.06 * sin(longitude * 13.0 - latitude * 29.0);
    float fine_structure = 0.94 + 0.06 * sin(longitude * 31.0 + latitude * 53.0);
    float density = max(
        (thin_disc + broad_disc + bulge) * structure * fine_structure
            * dust_transmission,
        0.0
    );

    float horizon_fade = smoothstep(0.0, 0.055, world_direction.y);
    float warm_core = exp(-sqr(longitude / 0.55) - sqr(latitude / 0.20));
    vec3 color = mix(
        vec3(0.48, 0.58, 0.82),
        vec3(0.92, 0.72, 0.50),
        warm_core * 0.72
    );
    color = color * rec709_to_working_color;
    // Lower contrast and a wider disc avoid the artificial vertical ribbon
    // produced by the previous narrow analytic profile in normal gameplay.
    return color * density * 0.030 * horizon_fade
        * ASTRO_MILKY_WAY_BRIGHTNESS * astro_night_visibility();
}

#endif // INCLUDE_SKY_ASTRONOMY_RENDERING
