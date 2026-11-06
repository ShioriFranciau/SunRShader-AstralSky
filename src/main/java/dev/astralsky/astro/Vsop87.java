package dev.astralsky.astro;

/**
 * Heliocentric positions from the VSOP87D theory using the same amplitude
 * truncated coefficient tables that the shader evaluates, so the two
 * implementations stay term-for-term identical.
 *
 * <p>Coordinates are spherical heliocentric ecliptic longitude, latitude and
 * distance referred to the mean ecliptic and equinox of date, matching the
 * frame used by the existing low-order solar and lunar ephemerides.
 */
public final class Vsop87 {
    /** Julian date of the J2000.0 epoch, 2000 January 1.5 TT. */
    public static final double J2000 = 2_451_545.0;

    private static final double DAYS_PER_JULIAN_MILLENNIUM = 365_250.0;

    private Vsop87() {
    }

    /** The eight bodies carried by the committed tables. */
    public enum Body {
        MERCURY("mercury", 2_439.7),
        VENUS("venus", 6_051.8),
        MARS("mars", 3_396.2),
        JUPITER("jupiter", 71_492.0),
        SATURN("saturn", 60_268.0),
        URANUS("uranus", 25_559.0),
        NEPTUNE("neptune", 24_764.0),
        EARTH("earth", 6_378.1);

        private final String id;
        private final double equatorialRadiusKm;

        Body(String id, double equatorialRadiusKm) {
            this.id = id;
            this.equatorialRadiusKm = equatorialRadiusKm;
        }

        public String id() {
            return id;
        }

        public double equatorialRadiusKm() {
            return equatorialRadiusKm;
        }
    }

    /** Heliocentric spherical coordinates in radians and astronomical units. */
    public record Spherical(double longitudeRadians, double latitudeRadians, double distanceAu) {
    }

    /** Right-handed Cartesian ecliptic coordinates in astronomical units. */
    public record Position(double x, double y, double z) {
        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    public static Spherical heliocentric(Body body, double julianDate) {
        return heliocentricAtMillennia(
            body, (julianDate - J2000) / DAYS_PER_JULIAN_MILLENNIUM
        );
    }

    /**
     * Evaluates the series directly from the Julian-millennia argument used by
     * the VSOP87 theory. Kept separate so tests can mirror the shader's time
     * computation exactly.
     */
    public static Spherical heliocentricAtMillennia(Body body, double millennia) {
        var data = Vsop87Tables.BODIES[body.ordinal()];
        double longitude = evaluate(data.longitude, data.longitudeCounts, millennia);
        double latitude = evaluate(data.latitude, data.latitudeCounts, millennia);
        double distance = evaluate(data.radius, data.radiusCounts, millennia);
        double normalizedLongitude = longitude % (2.0 * Math.PI);
        if (normalizedLongitude < 0.0) {
            normalizedLongitude += 2.0 * Math.PI;
        }
        return new Spherical(normalizedLongitude, latitude, distance);
    }

    public static Position rectangular(Spherical spherical) {
        double cosLatitude = Math.cos(spherical.latitudeRadians());
        return new Position(
            spherical.distanceAu() * cosLatitude * Math.cos(spherical.longitudeRadians()),
            spherical.distanceAu() * cosLatitude * Math.sin(spherical.longitudeRadians()),
            spherical.distanceAu() * Math.sin(spherical.latitudeRadians())
        );
    }

    /** Evaluates one flattened series, grouped by the power of the argument. */
    static double evaluate(double[] terms, int[] counts, double millennia) {
        double sum = 0.0;
        double power = 1.0;
        int offset = 0;
        for (int exponent = 0; exponent < counts.length; exponent++) {
            int count = counts[exponent];
            for (int index = 0; index < count; index++) {
                int base = (offset + index) * 3;
                sum += terms[base]
                    * Math.cos(terms[base + 1] + terms[base + 2] * millennia)
                    * power;
            }
            offset += count;
            power *= millennia;
        }
        return sum;
    }
}
