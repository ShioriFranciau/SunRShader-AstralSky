package dev.astralsky.astro;

/**
 * Small, dependency-free astronomy helpers shared by configuration previews
 * and the golden-reference tests for the shader implementation.
 */
public final class AstronomyMath {
    private static final double J2000 = 2_451_545.0;

    private AstronomyMath() {
    }

    public static double julianDate(int year, int month, int day, double utcHours) {
        int adjustedYear = year;
        int adjustedMonth = month;
        if (adjustedMonth <= 2) {
            adjustedYear--;
            adjustedMonth += 12;
        }

        int century = Math.floorDiv(adjustedYear, 100);
        int gregorianCorrection = 2 - century + Math.floorDiv(century, 4);

        return Math.floor(365.25 * (adjustedYear + 4716))
            + Math.floor(30.6001 * (adjustedMonth + 1))
            + day
            + gregorianCorrection
            - 1524.5
            + utcHours / 24.0;
    }

    public static double greenwichMeanSiderealDegrees(double julianDate) {
        double degrees = 280.46061837 + 360.98564736629 * (julianDate - J2000);
        return normalizeDegrees(degrees);
    }

    /**
     * Mirrors the precision-preserving calculation used by the GLSL shader.
     * It avoids representing the full Julian date as a 32-bit float.
     */
    public static double greenwichMeanSiderealDegreesGpuSafe(
        int year,
        int month,
        int day,
        double utcHours
    ) {
        int a = Math.floorDiv(14 - month, 12);
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        int julianDayNumber = day + Math.floorDiv(153 * m + 2, 5) + 365 * y
            + Math.floorDiv(y, 4) - Math.floorDiv(y, 100) + Math.floorDiv(y, 400) - 32045;

        int wholeDays = julianDayNumber - 2_451_545;
        double fractionalDay = (utcHours - 12.0) / 24.0;
        int dayCarry = (int) Math.floor(fractionalDay);
        wholeDays += dayCarry;
        fractionalDay -= dayCarry;

        double degrees = 280.46061837
            + 0.98564736629 * wholeDays
            + 360.98564736629 * fractionalDay;
        return normalizeDegrees(degrees);
    }

    /** Mirrors the shader's float-safe day count for the orbital elements' epoch. */
    public static double ephemerisDaysGpuSafe(int year, int month, int day, double utcHours) {
        int a = Math.floorDiv(14 - month, 12);
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        int julianDayNumber = day + Math.floorDiv(153 * m + 2, 5) + 365 * y
            + Math.floorDiv(y, 4) - Math.floorDiv(y, 100) + Math.floorDiv(y, 400) - 32045;
        int wholeDays = julianDayNumber - 2_451_545;
        double fractionalDay = (utcHours - 12.0) / 24.0;
        int dayCarry = (int) Math.floor(fractionalDay);
        return wholeDays + dayCarry + (fractionalDay - dayCarry) + 1.5;
    }

    public static double localSiderealDegrees(double julianDate, double longitudeDegreesEast) {
        return normalizeDegrees(greenwichMeanSiderealDegrees(julianDate) + longitudeDegreesEast);
    }

    public static HorizontalCoordinates equatorialToHorizontal(
        double rightAscensionDegrees,
        double declinationDegrees,
        double localSiderealDegrees,
        double latitudeDegrees
    ) {
        double hourAngle = Math.toRadians(normalizeSignedDegrees(localSiderealDegrees - rightAscensionDegrees));
        double declination = Math.toRadians(declinationDegrees);
        double latitude = Math.toRadians(latitudeDegrees);

        double cosDeclination = Math.cos(declination);
        double east = -cosDeclination * Math.sin(hourAngle);
        double north = Math.sin(declination) * Math.cos(latitude)
            - cosDeclination * Math.cos(hourAngle) * Math.sin(latitude);
        double up = Math.sin(declination) * Math.sin(latitude)
            + cosDeclination * Math.cos(hourAngle) * Math.cos(latitude);

        double altitude = Math.toDegrees(Math.asin(clamp(up, -1.0, 1.0)));
        double azimuth = normalizeDegrees(Math.toDegrees(Math.atan2(east, north)));
        return new HorizontalCoordinates(east, up, north, altitude, azimuth);
    }

    public static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    /** Inverse of the east/north/up transform, in J2000 equatorial coordinates. */
    public static EquatorialCoordinates horizontalToEquatorial(
        double east, double up, double north, double localSiderealDegrees, double latitudeDegrees
    ) {
        double latitude = Math.toRadians(latitudeDegrees);
        double length = Math.sqrt(east * east + up * up + north * north);
        if (!Double.isFinite(length) || length == 0.0) {
            throw new IllegalArgumentException("A finite nonzero direction is required");
        }
        east /= length;
        up /= length;
        north /= length;
        double declination = Math.asin(clamp(north * Math.cos(latitude) + up * Math.sin(latitude), -1.0, 1.0));
        double hourAngle = Math.atan2(-east, up * Math.cos(latitude) - north * Math.sin(latitude));
        return new EquatorialCoordinates(
            normalizeDegrees(localSiderealDegrees - Math.toDegrees(hourAngle)),
            Math.toDegrees(declination)
        );
    }

    /** IAU J2000 equatorial coordinates to Galactic longitude and latitude. */
    public static GalacticCoordinates equatorialToGalactic(
        double rightAscensionDegrees,
        double declinationDegrees
    ) {
        double rightAscension = Math.toRadians(rightAscensionDegrees);
        double declination = Math.toRadians(declinationDegrees);
        double cosDeclination = Math.cos(declination);
        double equatorialX = cosDeclination * Math.cos(rightAscension);
        double equatorialY = cosDeclination * Math.sin(rightAscension);
        double equatorialZ = Math.sin(declination);

        double galacticX = -0.0548755604 * equatorialX
            - 0.8734370902 * equatorialY
            - 0.4838350155 * equatorialZ;
        double galacticY = 0.4941094279 * equatorialX
            - 0.4448296300 * equatorialY
            + 0.7469822445 * equatorialZ;
        double galacticZ = -0.8676661490 * equatorialX
            - 0.1980763734 * equatorialY
            + 0.4559837762 * equatorialZ;

        return new GalacticCoordinates(
            normalizeDegrees(Math.toDegrees(Math.atan2(galacticY, galacticX))),
            Math.toDegrees(Math.asin(clamp(galacticZ, -1.0, 1.0)))
        );
    }

    /** Low-order apparent geocentric solar position, suitable for visual rendering. */
    public static CelestialCoordinates solarEquatorial(double julianDate) {
        double days = julianDate - 2_451_543.5;
        double perihelion = normalizeDegrees(282.9404 + 4.70935e-5 * days);
        double eccentricity = 0.016709 - 1.151e-9 * days;
        double meanAnomaly = normalizeDegrees(356.0470 + 0.9856002585 * days);
        double eccentricAnomaly = eccentricAnomalyDegrees(meanAnomaly, eccentricity);
        double x = Math.cos(Math.toRadians(eccentricAnomaly)) - eccentricity;
        double y = Math.sqrt(1.0 - eccentricity * eccentricity) * Math.sin(Math.toRadians(eccentricAnomaly));
        double trueAnomaly = Math.toDegrees(Math.atan2(y, x));
        double distance = Math.hypot(x, y);
        return eclipticToEquatorial(normalizeDegrees(trueAnomaly + perihelion), 0.0, distance, days);
    }

    /** Low-order geocentric lunar position including the dominant perturbations. */
    public static CelestialCoordinates lunarEquatorial(double julianDate) {
        double days = julianDate - 2_451_543.5;
        double node = normalizeDegrees(125.1228 - 0.0529538083 * days);
        double inclination = 5.1454;
        double periapsis = normalizeDegrees(318.0634 + 0.1643573223 * days);
        double eccentricity = 0.054900;
        double meanAnomaly = normalizeDegrees(115.3654 + 13.0649929509 * days);
        double eccentricAnomaly = eccentricAnomalyDegrees(meanAnomaly, eccentricity);
        double x = Math.cos(Math.toRadians(eccentricAnomaly)) - eccentricity;
        double y = Math.sqrt(1.0 - eccentricity * eccentricity) * Math.sin(Math.toRadians(eccentricAnomaly));
        double trueAnomaly = Math.toDegrees(Math.atan2(y, x));
        double distance = 60.2666 * Math.hypot(x, y);

        double argument = Math.toRadians(trueAnomaly + periapsis);
        double nodeRadians = Math.toRadians(node);
        double inclinationRadians = Math.toRadians(inclination);
        double eclipticX = distance * (
            Math.cos(nodeRadians) * Math.cos(argument)
                - Math.sin(nodeRadians) * Math.sin(argument) * Math.cos(inclinationRadians)
        );
        double eclipticY = distance * (
            Math.sin(nodeRadians) * Math.cos(argument)
                + Math.cos(nodeRadians) * Math.sin(argument) * Math.cos(inclinationRadians)
        );
        double eclipticZ = distance * Math.sin(argument) * Math.sin(inclinationRadians);
        double longitude = normalizeDegrees(Math.toDegrees(Math.atan2(eclipticY, eclipticX)));
        double latitude = Math.toDegrees(Math.atan2(eclipticZ, Math.hypot(eclipticX, eclipticY)));

        double solarPerihelion = normalizeDegrees(282.9404 + 4.70935e-5 * days);
        double solarMeanAnomaly = normalizeDegrees(356.0470 + 0.9856002585 * days);
        double solarMeanLongitude = normalizeDegrees(solarPerihelion + solarMeanAnomaly);
        double lunarMeanLongitude = normalizeDegrees(node + periapsis + meanAnomaly);
        double elongation = normalizeDegrees(lunarMeanLongitude - solarMeanLongitude);
        double argumentOfLatitude = normalizeDegrees(lunarMeanLongitude - node);

        longitude += -1.274 * sinDegrees(meanAnomaly - 2.0 * elongation)
            + 0.658 * sinDegrees(2.0 * elongation)
            - 0.186 * sinDegrees(solarMeanAnomaly)
            - 0.059 * sinDegrees(2.0 * meanAnomaly - 2.0 * elongation)
            - 0.057 * sinDegrees(meanAnomaly - 2.0 * elongation + solarMeanAnomaly)
            + 0.053 * sinDegrees(meanAnomaly + 2.0 * elongation)
            + 0.046 * sinDegrees(2.0 * elongation - solarMeanAnomaly)
            + 0.041 * sinDegrees(meanAnomaly - solarMeanAnomaly)
            - 0.035 * sinDegrees(elongation)
            - 0.031 * sinDegrees(meanAnomaly + solarMeanAnomaly)
            - 0.015 * sinDegrees(2.0 * argumentOfLatitude - 2.0 * elongation)
            + 0.011 * sinDegrees(meanAnomaly - 4.0 * elongation);
        latitude += -0.173 * sinDegrees(argumentOfLatitude - 2.0 * elongation)
            - 0.055 * sinDegrees(meanAnomaly - argumentOfLatitude - 2.0 * elongation)
            - 0.046 * sinDegrees(meanAnomaly + argumentOfLatitude - 2.0 * elongation)
            + 0.033 * sinDegrees(argumentOfLatitude + 2.0 * elongation)
            + 0.017 * sinDegrees(2.0 * meanAnomaly + argumentOfLatitude);
        distance += -0.58 * cosDegrees(meanAnomaly - 2.0 * elongation)
            - 0.46 * cosDegrees(2.0 * elongation);
        return eclipticToEquatorial(normalizeDegrees(longitude), latitude, distance, days);
    }

    /**
     * Ecliptic-of-date spherical coordinates to equatorial coordinates of date.
     * The VSOP87 planetary path and the low-order solar/lunar path share this
     * conversion so the whole solar system stays in one reference frame.
     */
    public static CelestialCoordinates eclipticToEquatorialOfDate(
        double longitudeDegrees,
        double latitudeDegrees,
        double distance,
        double julianDate
    ) {
        return eclipticToEquatorial(
            longitudeDegrees, latitudeDegrees, distance, julianDate - 2_451_543.5
        );
    }

    public static double lunarIlluminatedFraction(double julianDate) {
        var sun = solarEquatorial(julianDate);
        var moon = lunarEquatorial(julianDate);
        double separation = angularSeparationDegrees(sun, moon);
        return (1.0 - Math.cos(Math.toRadians(separation))) * 0.5;
    }

    /**
     * Apparent solar-eclipse geometry for an observer on a spherical Earth.
     * The topocentric lunar correction mirrors the shader implementation.
     */
    public static SolarEclipseCircumstances solarEclipseCircumstances(
        double julianDate,
        double longitudeDegreesEast,
        double latitudeDegrees
    ) {
        var sun = solarEquatorial(julianDate);
        var moon = lunarEquatorial(julianDate);
        double sidereal = localSiderealDegrees(julianDate, longitudeDegreesEast);
        var sunHorizontal = equatorialToHorizontal(
            sun.rightAscensionDegrees(), sun.declinationDegrees(), sidereal, latitudeDegrees
        );
        var geocentricMoon = equatorialToHorizontal(
            moon.rightAscensionDegrees(), moon.declinationDegrees(), sidereal, latitudeDegrees
        );

        double topocentricEast = geocentricMoon.east() * moon.distance();
        double topocentricUp = geocentricMoon.up() * moon.distance() - 1.0;
        double topocentricNorth = geocentricMoon.north() * moon.distance();
        double topocentricDistance = Math.sqrt(
            topocentricEast * topocentricEast
                + topocentricUp * topocentricUp
                + topocentricNorth * topocentricNorth
        );
        topocentricEast /= topocentricDistance;
        topocentricUp /= topocentricDistance;
        topocentricNorth /= topocentricDistance;

        double separation = Math.toDegrees(Math.acos(clamp(
            sunHorizontal.east() * topocentricEast
                + sunHorizontal.up() * topocentricUp
                + sunHorizontal.north() * topocentricNorth,
            -1.0,
            1.0
        )));
        double sunRadius = 0.2666;
        double moonRadius = 0.2725 * 60.2666 / topocentricDistance;
        double coverage = circleCoverageFraction(sunRadius, moonRadius, separation);
        return new SolarEclipseCircumstances(
            separation, sunRadius, moonRadius, coverage,
            sunHorizontal.altitudeDegrees(), sunHorizontal.azimuthDegrees()
        );
    }

    private static double circleCoverageFraction(
        double targetRadius,
        double occluderRadius,
        double separation
    ) {
        if (separation >= targetRadius + occluderRadius) {
            return 0.0;
        }
        if (separation <= Math.abs(occluderRadius - targetRadius)) {
            double smaller = Math.min(targetRadius, occluderRadius);
            return clamp(smaller * smaller / (targetRadius * targetRadius), 0.0, 1.0);
        }
        double separationSquared = separation * separation;
        double targetSquared = targetRadius * targetRadius;
        double occluderSquared = occluderRadius * occluderRadius;
        double targetSegment = Math.acos(clamp(
            (separationSquared + targetSquared - occluderSquared)
                / (2.0 * separation * targetRadius), -1.0, 1.0
        ));
        double occluderSegment = Math.acos(clamp(
            (separationSquared + occluderSquared - targetSquared)
                / (2.0 * separation * occluderRadius), -1.0, 1.0
        ));
        double triangle = 0.5 * Math.sqrt(Math.max(
            (-separation + targetRadius + occluderRadius)
                * (separation + targetRadius - occluderRadius)
                * (separation - targetRadius + occluderRadius)
                * (separation + targetRadius + occluderRadius),
            0.0
        ));
        double overlap = targetSquared * targetSegment
            + occluderSquared * occluderSegment - triangle;
        return clamp(overlap / (Math.PI * targetSquared), 0.0, 1.0);
    }

    public static double angularSeparationDegrees(CelestialCoordinates first, CelestialCoordinates second) {
        double firstRa = Math.toRadians(first.rightAscensionDegrees());
        double firstDec = Math.toRadians(first.declinationDegrees());
        double secondRa = Math.toRadians(second.rightAscensionDegrees());
        double secondDec = Math.toRadians(second.declinationDegrees());
        double cosine = Math.sin(firstDec) * Math.sin(secondDec)
            + Math.cos(firstDec) * Math.cos(secondDec) * Math.cos(firstRa - secondRa);
        return Math.toDegrees(Math.acos(clamp(cosine, -1.0, 1.0)));
    }

    /** Shadow-tip position of a vertical gnomon on level ground; east/north are in gnomon heights. */
    public static ShadowCoordinates verticalGnomonShadow(
        double julianDate,
        double longitudeDegreesEast,
        double latitudeDegrees
    ) {
        var sun = solarEquatorial(julianDate);
        var horizontal = equatorialToHorizontal(
            sun.rightAscensionDegrees(),
            sun.declinationDegrees(),
            localSiderealDegrees(julianDate, longitudeDegreesEast),
            latitudeDegrees
        );
        if (horizontal.up() <= 0.0) {
            throw new IllegalArgumentException("The Sun must be above the horizon");
        }
        return new ShadowCoordinates(-horizontal.east() / horizontal.up(), -horizontal.north() / horizontal.up());
    }

    private static CelestialCoordinates eclipticToEquatorial(
        double longitudeDegrees,
        double latitudeDegrees,
        double distance,
        double days
    ) {
        double longitude = Math.toRadians(longitudeDegrees);
        double latitude = Math.toRadians(latitudeDegrees);
        double obliquity = Math.toRadians(23.4393 - 3.563e-7 * days);
        double eclipticX = distance * Math.cos(longitude) * Math.cos(latitude);
        double eclipticY = distance * Math.sin(longitude) * Math.cos(latitude);
        double eclipticZ = distance * Math.sin(latitude);
        double equatorialX = eclipticX;
        double equatorialY = eclipticY * Math.cos(obliquity) - eclipticZ * Math.sin(obliquity);
        double equatorialZ = eclipticY * Math.sin(obliquity) + eclipticZ * Math.cos(obliquity);
        return new CelestialCoordinates(
            normalizeDegrees(Math.toDegrees(Math.atan2(equatorialY, equatorialX))),
            Math.toDegrees(Math.atan2(equatorialZ, Math.hypot(equatorialX, equatorialY))),
            distance
        );
    }

    private static double eccentricAnomalyDegrees(double meanAnomalyDegrees, double eccentricity) {
        double estimate = meanAnomalyDegrees
            + Math.toDegrees(eccentricity * Math.sin(Math.toRadians(meanAnomalyDegrees))
                * (1.0 + eccentricity * Math.cos(Math.toRadians(meanAnomalyDegrees))));
        for (int iteration = 0; iteration < 3; iteration++) {
            double radians = Math.toRadians(estimate);
            double errorDegrees = estimate - Math.toDegrees(eccentricity * Math.sin(radians)) - meanAnomalyDegrees;
            estimate -= errorDegrees / (1.0 - eccentricity * Math.cos(radians));
        }
        return estimate;
    }

    private static double sinDegrees(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double cosDegrees(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }

    private static double normalizeSignedDegrees(double degrees) {
        double normalized = normalizeDegrees(degrees);
        return normalized > 180.0 ? normalized - 360.0 : normalized;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record HorizontalCoordinates(
        double east,
        double up,
        double north,
        double altitudeDegrees,
        double azimuthDegrees
    ) {
    }

    public record EquatorialCoordinates(double rightAscensionDegrees, double declinationDegrees) {
    }

    public record GalacticCoordinates(double longitudeDegrees, double latitudeDegrees) {
    }

    public record CelestialCoordinates(
        double rightAscensionDegrees,
        double declinationDegrees,
        double distance
    ) {
    }

    public record ShadowCoordinates(double east, double north) {
    }

    public record SolarEclipseCircumstances(
        double centreSeparationDegrees,
        double solarRadiusDegrees,
        double lunarRadiusDegrees,
        double solarCoverageFraction,
        double solarAltitudeDegrees,
        double solarAzimuthDegrees
    ) {
    }
}

