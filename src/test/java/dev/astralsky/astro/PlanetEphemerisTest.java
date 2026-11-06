package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetEphemerisTest {
    /** Positions from the untruncated series, used as the reference. */
    private static final double[] GOLDEN_JULIAN_DATES = {
        2_460_409.25, // 2024-04-08 18:00 UTC
        2_459_205.25, // 2020-12-21 18:00 UTC
        2_460_691.5   // 2025-01-16 00:00 UTC
    };

    private static final double[][][] GOLDEN_POSITIONS = {
        {
            {21.91865, 12.24313}, {4.66820, 0.39044}, {344.85871, -7.80797},
            {46.82632, 16.71055}, {346.34368, -7.67014}, {48.82331, 17.79005},
            {358.83248, -1.83786}
        },
        {
            {271.39393, -25.00819}, {245.99242, -20.52959}, {21.23834, 9.61464},
            {302.80590, -20.51151}, {302.78348, -20.41196}, {34.77548, 13.40604},
            {349.68532, -5.61811}
        },
        {
            {282.12781, -23.77337}, {344.50387, -6.70230}, {119.15086, 25.10490},
            {70.48689, 21.66988}, {347.69440, -7.39225}, {51.02990, 18.36926},
            {358.27650, -2.13859}
        }
    };

    @Test
    void geocentricPositionsMatchTheReferenceSeries() {
        for (int epoch = 0; epoch < GOLDEN_JULIAN_DATES.length; epoch++) {
            double julianDate = GOLDEN_JULIAN_DATES[epoch];
            List<PlanetEphemeris.PlanetState> states = PlanetEphemeris.all(julianDate);
            assertEquals(7, states.size());
            for (int planet = 0; planet < states.size(); planet++) {
                PlanetEphemeris.PlanetState state = states.get(planet);
                double expectedRightAscension = GOLDEN_POSITIONS[epoch][planet][0];
                double expectedDeclination = GOLDEN_POSITIONS[epoch][planet][1];
                double rightAscensionError = Math.abs(
                    signedAngleDifference(state.rightAscensionDegrees(), expectedRightAscension)
                );
                assertTrue(
                    rightAscensionError < 0.003,
                    () -> state.body().id() + " right ascension is off by "
                        + rightAscensionError + " degrees at Julian date " + julianDate
                );
                assertEquals(
                    expectedDeclination,
                    state.declinationDegrees(),
                    0.003,
                    () -> state.body().id() + " declination at Julian date " + julianDate
                );
            }
        }
    }

    @Test
    void greatConjunctionOfDecember2020IsSixArcminutes() {
        var jupiter = PlanetEphemeris.state(Vsop87.Body.JUPITER, 2_459_205.25);
        var saturn = PlanetEphemeris.state(Vsop87.Body.SATURN, 2_459_205.25);
        double separationDegrees = AstronomyMath.angularSeparationDegrees(
            new AstronomyMath.CelestialCoordinates(
                jupiter.rightAscensionDegrees(),
                jupiter.declinationDegrees(),
                jupiter.distanceAu()
            ),
            new AstronomyMath.CelestialCoordinates(
                saturn.rightAscensionDegrees(),
                saturn.declinationDegrees(),
                saturn.distanceAu()
            )
        );
        // The real minimum separation on 2020-12-21 was about 6.1 arcminutes.
        assertEquals(0.1017, separationDegrees, 0.004);
    }

    @Test
    void marsOppositionOfJanuary2025() {
        var mars = PlanetEphemeris.state(Vsop87.Body.MARS, 2_460_691.5);
        assertTrue(
            mars.solarElongationDegrees() > 174.0,
            "Mars should be near opposition, elongation was "
                + mars.solarElongationDegrees()
        );
        assertTrue(mars.illuminatedFraction() > 0.999);
        assertEquals(-1.38, mars.magnitude(), 0.15);
        // Opposition distance is about 0.64 AU, so the disc is near its maximum.
        assertEquals(0.643, mars.distanceAu(), 0.02);
    }

    @Test
    void venusReachesGreatestEasternElongationInJanuary2025() {
        var venus = PlanetEphemeris.state(Vsop87.Body.VENUS, 2_460_680.5);
        assertTrue(
            venus.solarElongationDegrees() > 46.0
                && venus.solarElongationDegrees() < 48.0,
            "Venus elongation on 2025-01-10 was " + venus.solarElongationDegrees()
        );
        assertTrue(venus.illuminatedFraction() > 0.45 && venus.illuminatedFraction() < 0.55);
    }

    @Test
    void innerPlanetElongationsStayBounded() {
        double worstMercury = 0.0;
        double worstVenus = 0.0;
        for (double julianDate = 2_460_678.5; julianDate <= 2_461_774.5; julianDate += 2.0) {
            worstMercury = Math.max(
                worstMercury,
                PlanetEphemeris.state(Vsop87.Body.MERCURY, julianDate).solarElongationDegrees()
            );
            worstVenus = Math.max(
                worstVenus,
                PlanetEphemeris.state(Vsop87.Body.VENUS, julianDate).solarElongationDegrees()
            );
        }
        assertTrue(worstMercury < 29.0, "Mercury reached " + worstMercury + " degrees");
        assertTrue(worstVenus < 48.5, "Venus reached " + worstVenus + " degrees");
    }

    @Test
    void phaseAngleAndIlluminatedFractionAgree() {
        for (double julianDate = 2_460_678.5; julianDate <= 2_461_774.5; julianDate += 37.0) {
            for (PlanetEphemeris.PlanetState state : PlanetEphemeris.all(julianDate)) {
                double expected = 0.5 * (1.0 + Math.cos(Math.toRadians(
                    state.phaseAngleDegrees()
                )));
                assertEquals(expected, state.illuminatedFraction(), 1e-9);
                assertTrue(state.illuminatedFraction() >= 0.0);
                assertTrue(state.illuminatedFraction() <= 1.0);
            }
        }
    }

    @Test
    void angularRadiusFollowsDistanceAndDiscSize() {
        for (double julianDate = 2_460_678.5; julianDate <= 2_461_774.5; julianDate += 61.0) {
            for (PlanetEphemeris.PlanetState state : PlanetEphemeris.all(julianDate)) {
                double expected = Math.toDegrees(Math.asin(
                    state.body().equatorialRadiusKm()
                        / (state.distanceAu() * 149_597_870.7)
                ));
                assertEquals(expected, state.angularRadiusDegrees(), 1e-9);
            }
        }
        // Jupiter is the largest disc: roughly 15 to 25 arcseconds in radius.
        var jupiter = PlanetEphemeris.state(Vsop87.Body.JUPITER, 2_460_691.5);
        assertTrue(jupiter.angularRadiusDegrees() * 3600.0 > 15.0);
        assertTrue(jupiter.angularRadiusDegrees() * 3600.0 < 25.0);
    }

    @Test
    void magnitudesStayInPlausibleRanges() {
        double[] minimum = {99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0};
        double[] maximum = {-99.0, -99.0, -99.0, -99.0, -99.0, -99.0, -99.0};
        for (double julianDate = 2_460_678.5; julianDate <= 2_461_774.5; julianDate += 5.0) {
            List<PlanetEphemeris.PlanetState> states = PlanetEphemeris.all(julianDate);
            for (int planet = 0; planet < states.size(); planet++) {
                double magnitude = states.get(planet).magnitude();
                minimum[planet] = Math.min(minimum[planet], magnitude);
                maximum[planet] = Math.max(maximum[planet], magnitude);
            }
        }

        double[] expectedMinimum = {-2.3, -4.9, -3.1, -3.0, -0.2, 5.2, 7.6};
        double[] expectedMaximum = {6.0, -3.6, 2.6, -1.5, 1.8, 6.1, 8.2};
        for (int planet = 0; planet < states().size(); planet++) {
            String id = states().get(planet).id();
            assertTrue(
                minimum[planet] >= expectedMinimum[planet],
                id + " became brighter than expected: " + minimum[planet]
            );
            assertTrue(
                maximum[planet] <= expectedMaximum[planet],
                id + " became fainter than expected: " + maximum[planet]
            );
        }
    }

    @Test
    void earthHasNoGeocentricState() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PlanetEphemeris.state(Vsop87.Body.EARTH, 2_460_691.5)
        );
    }

    private static List<Vsop87.Body> states() {
        return List.of(
            Vsop87.Body.MERCURY, Vsop87.Body.VENUS, Vsop87.Body.MARS,
            Vsop87.Body.JUPITER, Vsop87.Body.SATURN, Vsop87.Body.URANUS,
            Vsop87.Body.NEPTUNE
        );
    }

    private static double signedAngleDifference(double first, double second) {
        double difference = (first - second) % 360.0;
        if (difference > 180.0) {
            difference -= 360.0;
        } else if (difference < -180.0) {
            difference += 360.0;
        }
        return difference;
    }
}
