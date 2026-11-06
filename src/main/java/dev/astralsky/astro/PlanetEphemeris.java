package dev.astralsky.astro;

import java.util.ArrayList;
import java.util.List;

/**
 * Geocentric planetary positions derived from the VSOP87 heliocentric series.
 *
 * <p>The result is the astrometric position referred to the mean equinox of
 * date: it excludes nutation, aberration and light-time, and uses the same
 * conversion that places the Sun and Moon. Light-time displaces the outer
 * planets by at most about twenty arcseconds, which is far below one rendered
 * pixel, so it is deliberately not modelled; the low-order solar and lunar
 * ephemerides already share that simplification.
 */
public final class PlanetEphemeris {
    private static final double KILOMETRES_PER_AU = 149_597_870.7;

    private PlanetEphemeris() {
    }

    /** Summary of one planet as seen from the centre of the Earth. */
    public record PlanetState(
        Vsop87.Body body,
        double rightAscensionDegrees,
        double declinationDegrees,
        double distanceAu,
        double heliocentricDistanceAu,
        double phaseAngleDegrees,
        double illuminatedFraction,
        double magnitude,
        double angularRadiusDegrees,
        double solarElongationDegrees
    ) {
    }

    public static List<PlanetState> all(double julianDate) {
        List<PlanetState> states = new ArrayList<>(7);
        for (Vsop87.Body body : Vsop87.Body.values()) {
            if (body != Vsop87.Body.EARTH) {
                states.add(state(body, julianDate));
            }
        }
        return states;
    }

    public static PlanetState state(Vsop87.Body body, double julianDate) {
        if (body == Vsop87.Body.EARTH) {
            throw new IllegalArgumentException("Earth has no geocentric position");
        }

        Vsop87.Position earth = Vsop87.rectangular(
            Vsop87.heliocentric(Vsop87.Body.EARTH, julianDate)
        );
        Vsop87.Position target = Vsop87.rectangular(
            Vsop87.heliocentric(body, julianDate)
        );

        double x = target.x() - earth.x();
        double y = target.y() - earth.y();
        double z = target.z() - earth.z();
        double distance = Math.sqrt(x * x + y * y + z * z);
        double heliocentricDistance = target.length();
        double earthDistance = earth.length();

        double cosPhase = clamp(
            (heliocentricDistance * heliocentricDistance + distance * distance
                - earthDistance * earthDistance)
                / (2.0 * heliocentricDistance * distance),
            -1.0,
            1.0
        );
        double phaseAngleDegrees = Math.toDegrees(Math.acos(cosPhase));
        double illuminatedFraction = 0.5 * (1.0 + cosPhase);

        double longitudeDegrees = Math.toDegrees(Math.atan2(y, x));
        double latitudeDegrees = Math.toDegrees(
            Math.atan2(z, Math.hypot(x, y))
        );
        var equatorial = AstronomyMath.eclipticToEquatorialOfDate(
            longitudeDegrees, latitudeDegrees, distance, julianDate
        );

        double angularRadiusDegrees = Math.toDegrees(Math.asin(Math.min(
            1.0,
            body.equatorialRadiusKm() / (distance * KILOMETRES_PER_AU)
        )));

        // Geocentric solar direction is the anti-Earth vector.
        double sunLength = earthDistance;
        double cosineElongation = clamp(
            (-x * earth.x() - y * earth.y() - z * earth.z())
                / (distance * sunLength),
            -1.0,
            1.0
        );
        double elongationDegrees = Math.toDegrees(Math.acos(cosineElongation));

        return new PlanetState(
            body,
            equatorial.rightAscensionDegrees(),
            equatorial.declinationDegrees(),
            distance,
            heliocentricDistance,
            phaseAngleDegrees,
            illuminatedFraction,
            magnitude(body, heliocentricDistance, distance, phaseAngleDegrees),
            angularRadiusDegrees,
            elongationDegrees
        );
    }

    /**
     * Apparent visual magnitude from the phase-angle polynomials for the
     * geometric distances (Meeus, Astronomical Algorithms, chapter 41). The
     * Saturn ring contribution is omitted, which costs at most about one
     * magnitude near a ring-plane crossing.
     */
    static double magnitude(
        Vsop87.Body body,
        double heliocentricDistance,
        double distance,
        double phaseAngleDegrees
    ) {
        double alpha = phaseAngleDegrees;
        double distanceTerm = 5.0 * Math.log10(heliocentricDistance * distance);
        return switch (body) {
            case MERCURY -> -0.42 + distanceTerm
                + 0.0380 * alpha
                - 0.000273 * alpha * alpha
                + 0.000002 * alpha * alpha * alpha;
            case VENUS -> -4.40 + distanceTerm
                + 0.0009 * alpha
                + 0.000239 * alpha * alpha
                - 0.00000065 * alpha * alpha * alpha;
            case MARS -> -1.52 + distanceTerm + 0.016 * alpha;
            case JUPITER -> -9.40 + distanceTerm + 0.005 * alpha;
            case SATURN -> -8.88 + distanceTerm + 0.044 * alpha;
            case URANUS -> -7.19 + distanceTerm;
            case NEPTUNE -> -6.87 + distanceTerm;
            case EARTH -> throw new IllegalArgumentException(
                "Earth has no apparent magnitude in this model"
            );
        };
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
