package dev.astralsky.astro;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the committed truncated tables against the full VSOP87D series in
 * {@code tools/vsop87d} and against the copy embedded in the shader pack.
 */
class Vsop87Test {
    private static final double J2000 = 2_451_545.0;
    private static final double DAYS_PER_MILLENNIUM = 365_250.0;
    private static final Path DATA_DIRECTORY = Path.of("tools/vsop87d");
    private static final Path SHADER_TABLES = Path.of(
        "shaderpack/SunRShader-AstralSky/shaders/include/sky/vsop87.glsl"
    );

    @Test
    void truncatedSeriesStayWithinThreeArcsecondsOfTheFullTheory() throws Exception {
        Map<Vsop87.Body, Series> full = loadFullSeries();
        double worstError = 0.0;
        String worstCase = "";

        // 2000-01-01 through 2050-12-31 in fifteen-day steps.
        for (double julianDate = 2_451_544.5; julianDate <= 2_469_806.5; julianDate += 15.0) {
            Vsop87.Position fullEarth = geocentricOrigin(full.get(Vsop87.Body.EARTH), julianDate);
            Vsop87.Position ourEarth = Vsop87.rectangular(
                Vsop87.heliocentric(Vsop87.Body.EARTH, julianDate)
            );
            for (Vsop87.Body body : Vsop87.Body.values()) {
                if (body == Vsop87.Body.EARTH) {
                    continue;
                }
                Vsop87.Position reference = geocentricOrigin(full.get(body), julianDate);
                Vsop87.Position candidate = Vsop87.rectangular(
                    Vsop87.heliocentric(body, julianDate)
                );
                double error = separationArcseconds(
                    reference.x() - fullEarth.x(),
                    reference.y() - fullEarth.y(),
                    reference.z() - fullEarth.z(),
                    candidate.x() - ourEarth.x(),
                    candidate.y() - ourEarth.y(),
                    candidate.z() - ourEarth.z()
                );
                if (error > worstError) {
                    worstError = error;
                    worstCase = body.id() + " at Julian date " + julianDate;
                }
            }
        }

        double measured = worstError;
        String measuredCase = worstCase;
        assertTrue(
            measured < 3.0,
            () -> "truncated VSOP87 error is " + measured + " arcseconds at " + measuredCase
        );
    }

    @Test
    void shaderTablesMatchTheJavaTables() throws Exception {
        String glsl = Files.readString(SHADER_TABLES);
        Matcher matcher = Pattern.compile(
            "const int (VSOP_([A-Z]+)_([LBR]))_N = (\\d+);"
        ).matcher(glsl);

        int arrays = 0;
        while (matcher.find()) {
            arrays++;
            Vsop87.Body body = Vsop87.Body.valueOf(matcher.group(2));
            Vsop87Tables.BodyData data = Vsop87Tables.BODIES[body.ordinal()];
            int declared = Integer.parseInt(matcher.group(4));
            int expected = switch (matcher.group(3)) {
                case "L" -> sum(data.longitudeCounts);
                case "B" -> sum(data.latitudeCounts);
                default -> sum(data.radiusCounts);
            };
            assertEquals(
                expected,
                declared,
                () -> matcher.group(1) + " disagrees between Java and GLSL"
            );
            assertTrue(
                glsl.contains(
                    "const vec4 " + matcher.group(1) + "[" + matcher.group(1) + "_N]"
                ),
                matcher.group(1)
            );
        }
        assertEquals(24, arrays, "expected one array per body and coordinate");
    }

    @Test
    void seriesTermCountsMatchTheirArrays() {
        for (Vsop87.Body body : Vsop87.Body.values()) {
            Vsop87Tables.BodyData data = Vsop87Tables.BODIES[body.ordinal()];
            assertEquals(body.id(), data.id);
            assertEquals(sum(data.longitudeCounts) * 3, data.longitude.length);
            assertEquals(sum(data.latitudeCounts) * 3, data.latitude.length);
            assertEquals(sum(data.radiusCounts) * 3, data.radius.length);
            assertEquals(6, data.longitudeCounts.length);
        }
        assertEquals(Vsop87.Body.EARTH.ordinal(), Vsop87Tables.EARTH_INDEX);
    }

    @Test
    void earthHeliocentricPositionReproducesTheLowOrderSolarTheory() {
        double worstError = 0.0;
        for (double julianDate = 2_451_545.0; julianDate <= 2_469_806.5; julianDate += 30.0) {
            Vsop87.Position earth = Vsop87.rectangular(
                Vsop87.heliocentric(Vsop87.Body.EARTH, julianDate)
            );
            double longitude = Math.toDegrees(Math.atan2(-earth.y(), -earth.x()));
            double latitude = Math.toDegrees(
                Math.atan2(-earth.z(), Math.hypot(earth.x(), earth.y()))
            );
            var vsopSun = AstronomyMath.eclipticToEquatorialOfDate(
                longitude, latitude, earth.length(), julianDate
            );
            worstError = Math.max(
                worstError,
                AstronomyMath.angularSeparationDegrees(
                    vsopSun, AstronomyMath.solarEquatorial(julianDate)
                )
            );
        }
        double measured = worstError;
        assertTrue(
            measured < 0.05,
            () -> "VSOP87 Earth and the low-order Sun differ by " + measured + " degrees"
        );
    }

    private record Series(
        double[] longitude,
        int[] longitudeCounts,
        double[] latitude,
        int[] latitudeCounts,
        double[] radius,
        int[] radiusCounts
    ) {
    }

    private static Map<Vsop87.Body, Series> loadFullSeries() throws Exception {
        Map<Vsop87.Body, Series> series = new EnumMap<>(Vsop87.Body.class);
        for (Vsop87.Body body : Vsop87.Body.values()) {
            series.put(body, readSeries(DATA_DIRECTORY.resolve(body.id() + ".csv")));
        }
        return series;
    }

    private static Series readSeries(Path path) throws Exception {
        List<List<double[]>> longitude = emptyPowers();
        List<List<double[]>> latitude = emptyPowers();
        List<List<double[]>> radius = emptyPowers();

        for (String line : Files.readAllLines(path)) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 4 || fields[0].length() < 2) {
                continue;
            }
            List<List<double[]>> target = switch (fields[0].charAt(0)) {
                case 'L' -> longitude;
                case 'B' -> latitude;
                case 'R' -> radius;
                default -> null;
            };
            if (target == null) {
                continue;
            }
            int power = fields[0].charAt(1) - '0';
            if (power < 0 || power >= 6) {
                continue;
            }
            target.get(power).add(new double[] {
                Double.parseDouble(fields[1]) * 1e-8,
                Double.parseDouble(fields[2]),
                Double.parseDouble(fields[3])
            });
        }

        return new Series(
            flatten(longitude), counts(longitude),
            flatten(latitude), counts(latitude),
            flatten(radius), counts(radius)
        );
    }

    private static List<List<double[]>> emptyPowers() {
        List<List<double[]>> powers = new ArrayList<>(6);
        for (int power = 0; power < 6; power++) {
            powers.add(new ArrayList<>());
        }
        return powers;
    }

    private static double[] flatten(List<List<double[]>> powers) {
        int total = 0;
        for (List<double[]> terms : powers) {
            total += terms.size();
        }
        double[] flattened = new double[total * 3];
        int index = 0;
        for (List<double[]> terms : powers) {
            for (double[] term : terms) {
                flattened[index++] = term[0];
                flattened[index++] = term[1];
                flattened[index++] = term[2];
            }
        }
        return flattened;
    }

    private static int[] counts(List<List<double[]>> powers) {
        int[] counts = new int[powers.size()];
        for (int power = 0; power < powers.size(); power++) {
            counts[power] = powers.get(power).size();
        }
        return counts;
    }

    private static Vsop87.Position geocentricOrigin(Series series, double julianDate) {
        double t = (julianDate - J2000) / DAYS_PER_MILLENNIUM;
        double longitude = Vsop87.evaluate(series.longitude(), series.longitudeCounts(), t);
        double latitude = Vsop87.evaluate(series.latitude(), series.latitudeCounts(), t);
        double distance = Vsop87.evaluate(series.radius(), series.radiusCounts(), t);
        double cosLatitude = Math.cos(latitude);
        return new Vsop87.Position(
            distance * cosLatitude * Math.cos(longitude),
            distance * cosLatitude * Math.sin(longitude),
            distance * Math.sin(latitude)
        );
    }

    private static double separationArcseconds(
        double ax, double ay, double az,
        double bx, double by, double bz
    ) {
        double lengthA = Math.sqrt(ax * ax + ay * ay + az * az);
        double lengthB = Math.sqrt(bx * bx + by * by + bz * bz);
        double cosine = (ax * bx + ay * by + az * bz) / (lengthA * lengthB);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine)))) * 3600.0;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }
}
