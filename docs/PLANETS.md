# Planets

Mercury through Neptune are positioned with the VSOP87D theory in the same
ecliptic-of-date frame as the Sun and Moon. The companion mod keeps a double
precision reference implementation; the shader pack evaluates the identical
truncated series, so both agree term for term.

## Data

`tools/vsop87d/<body>.csv` holds the complete VSOP87D series as published by
CDS (VI/81) and redistributed by the `pymeeus` package. Each line is

```
L0 440250710.144000 0.00000000000 0.00000000000
```

that is `series identifier`, `A (1e-8 rad, or 1e-8 AU for R)`, `B (rad)` and
`C (rad per Julian millennium)`. The identifier is the coordinate (`L`, `B`,
`R`) followed by the power of `t`. `tools/vsop87d/README.md` records the exact
regeneration command; the CSV files are the authoritative source and are never
regenerated implicitly.

## Truncation

The series are cut by amplitude. `tools/Vsop87Generator.java` keeps every term
whose amplitude exceeds

| coordinate | cutoff | rationale |
| --- | --- | --- |
| `L`, `B` of the seven planets | `3.0e-7` rad (0.06 arcsec) | below the float limit of the shader |
| `L`, `B` of Earth | `1.0e-7` rad (0.02 arcsec) | Earth's error enters every geocentric vector |
| `R` | `1.0e-6` AU (150 km) | distance only affects size and brightness |

This leaves 2,676 terms:

| body | `L` | `B` | `R` | total |
| --- | --- | --- | --- | --- |
| Mercury | 96 | 48 | 24 | 168 |
| Venus | 58 | 26 | 16 | 100 |
| Mars | 174 | 46 | 73 | 293 |
| Jupiter | 207 | 66 | 156 | 429 |
| Saturn | 297 | 106 | 285 | 688 |
| Uranus | 210 | 66 | 314 | 590 |
| Neptune | 77 | 40 | 125 | 242 |
| Earth | 130 | 13 | 23 | 166 |

The geocentric direction error against the untruncated series stays below two
arcseconds between 2000 and 2050 (measured maximum per body: Mercury 1.03,
Venus 1.90, Mars 1.98, Jupiter 0.76, Saturn 0.93, Uranus 0.63, Neptune 0.29
arcseconds). `Vsop87Test` re-measures this bound against the CSV data.

## Evaluation

With `t = (JD - 2451545.0) / 365250` (Julian millennia from J2000.0), each
coordinate is

```
X(t) = sum over k of  t^k * sum over terms of  A * cos(B + C * t)
```

The result is heliocentric spherical longitude, latitude and distance referred
to the mean ecliptic and equinox of date. Longitude is reduced to `[0, 2pi)`.

Both implementations are generated from the same cutoff, so a term kept in
`Vsop87Tables.java` is kept in `vsop87.glsl`; `Vsop87Test` asserts the array
sizes match.

## Geocentric quantities

For a planet `p` and Earth `e` the geocentric vector is `p - e`, and with
`r = |p|`, `d = |p - e|` and `R = |e|`:

- phase angle `alpha = acos((r^2 + d^2 - R^2) / (2 r d))`;
- illuminated fraction `k = (1 + cos alpha) / 2`;
- apparent magnitude from the Meeus phase polynomials, using `5 log10(r d)` plus
  a per-planet alpha polynomial (Saturn's ring term is omitted, about one
  magnitude near a ring-plane crossing);
- angular radius `asin(equatorial radius / d)`;
- solar elongation from the angle between the geocentric planet and the
  anti-Earth direction.

Positions are astrometric of date: nutation, aberration and light-time are
omitted, matching the existing low-order solar and lunar ephemerides. Light-time
displaces the outer planets by at most about twenty arcseconds, well below one
rendered pixel.

## Rendering

Evaluating 2,676 terms per sky pixel is wasteful, so the frame is split:

1. `program/d4a_generate_sky_sh.csh` (stage `deferred4_a`) includes
   `include/sky/planet_state.glsl`. A single invocation evaluates every planet
   and writes two texels per planet into the `planet_data_img` image:
   `(world direction, angular radius)` and `(tinted radiance, 1.0)`.
2. `include/sky/planets.glsl` is included by the deferred sky path, reads the
   image back through its sampler with `texelFetch`, and adds a Gaussian disc.

The compute pass writes the table before the `SH_SKYLIGHT` early return, so
planets render even when skylight spherical harmonics are disabled.

The physical angular radius is smaller than one pixel at normal fields of view,
so `ASTRO_PLANET_MIN_RADIUS` (0.0007 rad, about 2.4 arcminutes) sets the floor
and `ASTRO_PLANET_SIZE` scales it. Radiance is calibrated to a reference
magnitude of -1: Jupiter sits near unit brightness, Venus blooms, and Uranus
and Neptune need a raised `ASTRO_PLANET_BRIGHTNESS` to be seen at all, which
matches their naked-eye visibility.

Planets are gated by `WORLD_OVERWORLD`, `ASTRO_REAL_SKY` and `ASTRO_PLANETS`.
The Nether and the End keep the previous behaviour, and the sky-map reflection
pass deliberately excludes planets so a bright planet cannot produce an
analytic glint.

## Precision

The shader evaluates in `float`. `C * t` for Mercury reaches `2.7e5 * 0.027`,
where a single-precision product is only accurate to about `1e-3` radians times
the term amplitude; the accumulated worst case is roughly ten arcseconds even
though the truncated series itself is accurate to two. That is sub-pixel at any
field of view, so the shader stays in `float` instead of carrying a double
precision emulation.

## Options

| option | default | effect |
| --- | --- | --- |
| `ASTRO_PLANETS` | on | master toggle |
| `ASTRO_PLANET_BRIGHTNESS` | `1.0` | radiance multiplier |
| `ASTRO_PLANET_SIZE` | `1.0` | display radius multiplier |
| `ASTRO_PLANET_MIN_RADIUS` | `0.0007` | minimum radius in radians |

## Validation

- `Vsop87Test`: truncation bound against the full CSV series, Java/GLSL table
  parity, Earth versus the low-order solar theory.
- `PlanetEphemerisTest`: geocentric right ascension and declination against
  reference values from the untruncated series at three epochs, the 2020-12-21
  Jupiter/Saturn conjunction (6.1 arcminutes), the 2025-01-16 Mars opposition,
  Venus' 2025-01-10 greatest elongation, elongation bounds, phase consistency,
  disc size and magnitude ranges.
- `ShaderPlanetIntegrationTest`: the compute pass, image registration, draw
  path, options and language entries stay wired together.
- The full-series pipeline was also cross-checked against PyEphem; agreement is
  between 0.1 and 1.0 arcminutes, which is the expected astrometric-versus-
  apparent offset.
