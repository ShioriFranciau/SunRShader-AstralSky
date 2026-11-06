# SunRShader AstralSky

SunRShader AstralSky combines the complete SunRShader/Photon visual pipeline
with a date- and location-dependent astronomical night sky for Minecraft 26.3,
Fabric Loader 0.20.1 and Iris 1.11.7.

The delivered pair consists of:

- `astralsky-1.4.0.jar`: a client-side Fabric companion that validates the
  observation configuration and injects it into the selected shader pack;
- `SunRShader-AstralSky-1.4.0.zip`: an Iris shader pack based on the supplied
  `SunRShader.cPm`, retaining its clouds, shadows, water, labPBR materials,
  volumetrics and post-processing.

## Astronomical sky

- selectable BSC5, 118,218-entry Hipparcos and 2,539,913-entry Tycho-2 star
  catalogues, rendered at J2000/ICRS right ascension and declination;
- local sidereal rotation derived from latitude, east-positive longitude,
  date and time;
- system-realtime, fixed-date and fixed-start/game-elapsed modes; the
  advancing mode initializes the game clock to the configured local hour and
  converts civil time with the configured UTC offset;
- date-dependent low-order solar/lunar ephemerides and continuously rendered
  physical Moon phases, including lunar-distance scaling and earthshine;
- VSOP87D heliocentric series for Mercury through Neptune, evaluated once per
  frame in the deferred compute pass and drawn as phase-lit discs with
  calculated magnitude, elongation and angular size;
- NASA LRO/LOLA lunar surface colour mapped in the calculated IAU lunar body
  frame, including sky position angle and low-order optical libration;
- observer-relative lunar parallax, physical Sun/Moon disc overlap, coronal
  glow and scene-light attenuation for partial and total solar eclipses;
- correctly oriented Galactic plane with a widened, low-contrast procedural
  Milky Way appearance;
- horizon extinction, weather visibility and configurable twinkle;
- SunR atmosphere and cloud occlusion retained, with astronomical Sun/Moon
  directions supplied to the sky-scattering passes.
- solar position checked against the straight equinox and oppositely curved
  solstice traces of a vertical gnomon's shadow endpoint;
- block-lighting and Iris shadow-map projection driven by the same real solar
  or lunar direction as the rendered celestial disc;
- cloud-composited sky-map reflections prevent an analytic Moon glint from
  leaking through cloud cover on nearby or distant water.

## Build

Java 25 is required.

```powershell
.\scripts\build.ps1
```

Artifacts are written to:

```text
build/libs/astralsky-1.4.0.jar
build/distributions/SunRShader-AstralSky-1.4.0.zip
```

## Deploy to the isolated PCL2 instance

```powershell
.\scripts\deploy-dev.ps1 -Build
```

The default instance is `D:\PCL2\.minecraft\versions\26.3-Fabric`. The
script installs the mod and an unpacked development copy of the shader pack.

Select `SunRShader-AstralSky` in Iris. Press `K` in game to choose the time
mode and edit every astronomical time/location value with sliders or exact
numeric entry. Saving automatically rebuilds the Iris pipeline. Iris'
Astronomy page contains visual controls
only, so it cannot conflict with the companion configuration. The companion
supports both unpacked and ZIP shader packs. See
[`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) for all fields.

SunRShader/Photon attribution and upstream license files are retained inside
the shader-pack artifact.
