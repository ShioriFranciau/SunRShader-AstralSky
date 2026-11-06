package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AstronomyMathTest {
    @Test
    void solarAndLunarPositionsTrackThe2024TotalEclipse() {
        double jd = AstronomyMath.julianDate(2024, 4, 8, 18.2875);
        var sun = AstronomyMath.solarEquatorial(jd);
        var moon = AstronomyMath.lunarEquatorial(jd);

        assertEquals(17.90375, sun.rightAscensionDegrees(), 0.25);
        assertEquals(7.5915, sun.declinationDegrees(), 0.25);
        assertEquals(17.7396, moon.rightAscensionDegrees(), 0.75);
        assertEquals(7.8988, moon.declinationDegrees(), 0.75);
        assertTrue(AstronomyMath.angularSeparationDegrees(sun, moon) < 0.75);
    }

    @Test
    void topocentricParallaxPlaces2024TotalityOverDallas() {
        // NASA lists maximum eclipse in Dallas at 13:42 CDT = 18:42 UTC.
        double jd = AstronomyMath.julianDate(2024, 4, 8, 18.7);
        var dallas = AstronomyMath.solarEclipseCircumstances(
            jd, -96.7970, 32.7767
        );
        var shanghai = AstronomyMath.solarEclipseCircumstances(
            jd, 121.4737, 31.2304
        );

        assertTrue(dallas.solarCoverageFraction() > 0.99, dallas.toString());
        assertTrue(dallas.lunarRadiusDegrees() > dallas.solarRadiusDegrees());
        assertTrue(dallas.solarAltitudeDegrees() > 60.0);
        assertEquals(0.0, shanghai.solarCoverageFraction(), 1.0e-12);
    }

    @Test
    void lunarIlluminationMatchesPublishedPrimaryPhases() {
        double fullMoon = AstronomyMath.julianDate(2024, 3, 25, 7.0);
        double newMoon = AstronomyMath.julianDate(2024, 4, 8, 18.35);
        assertTrue(AstronomyMath.lunarIlluminatedFraction(fullMoon) > 0.99);
        assertTrue(AstronomyMath.lunarIlluminatedFraction(newMoon) < 0.01);
    }

    @Test
    void lunarIlluminationMatchesPublishedQuarterPhases() {
        double firstQuarter = AstronomyMath.julianDate(2024, 4, 15, 19.0 + 13.0 / 60.0);
        double lastQuarter = AstronomyMath.julianDate(2024, 4, 2, 3.25);

        assertEquals(0.5, AstronomyMath.lunarIlluminatedFraction(firstQuarter), 0.025);
        assertEquals(0.5, AstronomyMath.lunarIlluminatedFraction(lastQuarter), 0.025);
    }

    @Test
    void iauLunarPrimeMeridianFacesEarthAtJ2000() {
        var moon = AstronomyMath.lunarEquatorial(2451545.0);
        double ra = Math.toRadians(moon.rightAscensionDegrees());
        double dec = Math.toRadians(moon.declinationDegrees());
        double[] earthFromMoon = {
            -Math.cos(dec) * Math.cos(ra),
            -Math.cos(dec) * Math.sin(ra),
            -Math.sin(dec)
        };

        double poleRa = Math.toRadians(269.9949);
        double poleDec = Math.toRadians(66.5392);
        double w = Math.toRadians(38.3213);
        double[] node = {-Math.sin(poleRa), Math.cos(poleRa), 0.0};
        double[] quadrature = {
            -Math.sin(poleDec) * Math.cos(poleRa),
            -Math.sin(poleDec) * Math.sin(poleRa),
            Math.cos(poleDec)
        };
        double[] primeMeridian = {
            Math.cos(w) * node[0] + Math.sin(w) * quadrature[0],
            Math.cos(w) * node[1] + Math.sin(w) * quadrature[1],
            Math.cos(w) * node[2] + Math.sin(w) * quadrature[2]
        };
        double alignment = primeMeridian[0] * earthFromMoon[0]
            + primeMeridian[1] * earthFromMoon[1]
            + primeMeridian[2] * earthFromMoon[2];

        assertTrue(alignment > Math.cos(Math.toRadians(10.0)), "alignment=" + alignment);
    }

    @Test
    void equinoxMeridianSunFacesTheEquatorInBothHemispheres() {
        double jd = AstronomyMath.julianDate(2024, 3, 20, 12.0);
        var sun = AstronomyMath.solarEquatorial(jd);
        double sidereal = AstronomyMath.localSiderealDegrees(jd, 0.0);
        var northern = AstronomyMath.equatorialToHorizontal(
            sun.rightAscensionDegrees(), sun.declinationDegrees(), sidereal, 35.0
        );
        var southern = AstronomyMath.equatorialToHorizontal(
            sun.rightAscensionDegrees(), sun.declinationDegrees(), sidereal, -35.0
        );

        assertTrue(northern.altitudeDegrees() > 54.0);
        assertEquals(180.0, northern.azimuthDegrees(), 5.0);
        assertTrue(southern.altitudeDegrees() > 54.0);
        assertTrue(southern.azimuthDegrees() < 5.0 || southern.azimuthDegrees() > 355.0);
    }

    @Test
    void juneSolsticeSolarDeclinationIsNearTheTropic() {
        var sun = AstronomyMath.solarEquatorial(AstronomyMath.julianDate(2024, 6, 20, 20.85));
        assertEquals(23.44, sun.declinationDegrees(), 0.15);
    }

    @Test
    void iauGalacticPoleAndCentreMatchJ2000References() {
        var northPole = AstronomyMath.equatorialToGalactic(192.85948, 27.12825);
        assertEquals(90.0, northPole.latitudeDegrees(), 5.0e-4);

        var centre = AstronomyMath.equatorialToGalactic(266.4051, -28.936175);
        double longitudeError = Math.abs(AstronomyMath.normalizeDegrees(centre.longitudeDegrees() + 180.0) - 180.0);
        assertEquals(0.0, longitudeError, 1.0e-4);
        assertEquals(0.0, centre.latitudeDegrees(), 1.0e-4);
    }

    @Test
    void equatorialRoundTripCoversHemispheresAndRightAscensionSeam() {
        for (double latitude : new double[] {-89.0, -33.8688, 0.0, 31.2304, 89.0}) {
            for (double rightAscension : new double[] {0.001, 79.1723, 180.0, 359.999}) {
                for (double declination : new double[] {-89.0, -45.0, 0.0, 45.9979, 89.0}) {
                    var horizontal = AstronomyMath.equatorialToHorizontal(rightAscension, declination, 38.9794, latitude);
                    var actual = AstronomyMath.horizontalToEquatorial(
                        horizontal.east(), horizontal.up(), horizontal.north(), 38.9794, latitude
                    );
                    double error = Math.abs(AstronomyMath.normalizeDegrees(actual.rightAscensionDegrees() - rightAscension + 180.0) - 180.0);
                    assertEquals(0.0, error, 1.0e-9);
                    assertEquals(declination, actual.declinationDegrees(), 1.0e-9);
                }
            }
        }
    }
    @Test
    void j2000EpochHasCanonicalJulianDate() {
        assertEquals(2_451_545.0, AstronomyMath.julianDate(2000, 1, 1, 12.0), 1.0e-9);
    }

    @Test
    void j2000GmstMatchesReferenceAngle() {
        assertEquals(280.46061837, AstronomyMath.greenwichMeanSiderealDegrees(2_451_545.0), 1.0e-9);
    }

    @Test
    void gpuSafeSiderealCalculationRetainsSubDayPrecision() {
        double utcHours = 13.901753333333334;
        double jd = AstronomyMath.julianDate(2026, 10, 30, utcHours);
        double reference = AstronomyMath.greenwichMeanSiderealDegrees(jd);
        double gpuSafe = AstronomyMath.greenwichMeanSiderealDegreesGpuSafe(2026, 10, 30, utcHours);

        assertEquals(reference, gpuSafe, 1.0e-7);
    }

    @Test
    void gpuSafeEphemerisUsesTheSameEpochAsTheOrbitalElements() {
        for (double hour : new double[] {-3.0, 0.0, 12.0, 23.999}) {
            double expected = AstronomyMath.julianDate(2026, 11, 3, hour) - 2_451_543.5;
            assertEquals(expected, AstronomyMath.ephemerisDaysGpuSafe(2026, 11, 3, hour), 1.0e-9);
        }
        assertEquals(1.5, AstronomyMath.ephemerisDaysGpuSafe(2000, 1, 1, 12.0), 1.0e-12);
    }

    @Test
    void equinoxGnomonTraceIsAnEastWestLine() {
        double latitude = 35.0;
        double noonNorth = AstronomyMath.verticalGnomonShadow(
            AstronomyMath.julianDate(2024, 3, 20, 12.0), 0.0, latitude
        ).north();
        for (double hour : new double[] {8.0, 9.0, 10.0, 11.0, 13.0, 14.0, 15.0, 16.0}) {
            var shadow = AstronomyMath.verticalGnomonShadow(
                AstronomyMath.julianDate(2024, 3, 20, hour), 0.0, latitude
            );
            assertEquals(noonNorth, shadow.north(), 0.025);
        }
    }

    @Test
    void solsticeGnomonTracesCurveInOppositeDirections() {
        double latitude = 35.0;
        double juneNoon = shadowNorth(2024, 6, 20, 12.0, latitude);
        double juneWing = (shadowNorth(2024, 6, 20, 9.0, latitude)
            + shadowNorth(2024, 6, 20, 15.0, latitude)) * 0.5;
        double decemberNoon = shadowNorth(2024, 12, 21, 12.0, latitude);
        double decemberWing = (shadowNorth(2024, 12, 21, 9.0, latitude)
            + shadowNorth(2024, 12, 21, 15.0, latitude)) * 0.5;

        assertTrue(juneWing < juneNoon, "positive declination must bend the trace southward at its wings");
        assertTrue(decemberWing > decemberNoon, "negative declination must bend the trace northward at its wings");
    }

    @Test
    void summerAndWinterShadowTracesMirrorAcrossHemispheres() {
        double northSummer = shadowNorth(2024, 6, 20, 12.0, 35.0);
        double southSummer = shadowNorth(2024, 12, 21, 12.0, -35.0);
        assertEquals(northSummer, -southSummer, 0.015);
    }

    private static double shadowNorth(int year, int month, int day, double hour, double latitude) {
        return AstronomyMath.verticalGnomonShadow(
            AstronomyMath.julianDate(year, month, day, hour), 0.0, latitude
        ).north();
    }

    @Test
    void meridianStarAtObserverDeclinationIsAtZenith() {
        double latitude = 31.2304;
        double localSidereal = 123.0;
        var horizontal = AstronomyMath.equatorialToHorizontal(
            localSidereal,
            latitude,
            localSidereal,
            latitude
        );

        assertEquals(90.0, horizontal.altitudeDegrees(), 1.0e-7);
        assertEquals(1.0, horizontal.up(), 1.0e-12);
    }

    @Test
    void polarisAltitudeTracksShanghaiLatitude() {
        double longitude = 121.4737;
        double localSolarHour = 22.0;
        double utcHour = localSolarHour - longitude / 15.0;
        double jd = AstronomyMath.julianDate(2026, 10, 30, utcHour);
        double lst = AstronomyMath.localSiderealDegrees(jd, longitude);

        var polaris = AstronomyMath.equatorialToHorizontal(37.9546, 89.2641, lst, 31.2304);
        assertTrue(polaris.altitudeDegrees() > 30.4 && polaris.altitudeDegrees() < 32.1);
    }
}

