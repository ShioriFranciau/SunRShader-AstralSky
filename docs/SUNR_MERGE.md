# SunRShader integration

The production shader pack is a complete copy of the user-supplied source:

```text
D:\PCL2\.minecraft\versions\1.20.1-Fabric 0.19.3\shaderpacks\SunRShader.cPm
```

It is packaged as `SunRShader-AstralSky`. The original render stages, images,
world variants, Photon/SunR lighting, volumetric clouds, water, material maps,
shadows and post-processing are preserved.

For the overworld real-sky path, Photon's direct-light vector and the shadow
map camera are rebuilt from the same astronomical Sun/Moon direction used by
the sky renderer. This avoids the vanilla shadow path remaining perpendicular
to a date- and latitude-dependent solar azimuth.

The astronomical integration is deliberately narrow:

- `shaders/include/sky/astronomy.glsl` calculates observation time, Julian-day
  offset, local sidereal time, solar/lunar ephemerides and coordinate transforms;
- the original random overworld stars are replaced by selectable BSC5,
  Hipparcos and 2.54-million-entry Tycho-2 atlases;
- the original fixed galaxy texture is replaced by a Galactic plane whose
  orientation follows the same date/location transform;
- SunR's atmosphere receives the astronomical Sun/Moon directions; its clouds,
  fog, tonemapping and sky reflections continue to composite over the sky;
- the Moon disc uses solar illumination geometry, lunar-distance scaling and
  earthshine instead of Minecraft's eight-step phase texture;
- the Moon samples NASA's LRO-derived global colour map through the calculated
  IAU pole and prime-meridian body frame, so lunar north, surface rotation and
  optical libration follow the configured date instead of the screen horizon;
- lunar direction is corrected from geocentric to observer-relative
  topocentric coordinates; the physical lunar disc masks the photosphere,
  while overlap coverage dims direct sunlight and reveals a compact corona;
- Nether and End star rendering retain the original SunR behavior.

The supplied source license, upstream README and vendor metadata remain in the
root of the delivered ZIP.
