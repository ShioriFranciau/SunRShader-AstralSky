# Phase 1 verification

## Automated checks

Run `scripts/build.ps1`. In addition to the Phase 0 astronomy checks, the test
suite verifies the IAU J2000 Galactic north pole and Galactic-centre direction.

## Runtime checks

1. Deploy while Minecraft is closed with `scripts/deploy-dev.ps1 -Build`.
2. Start the isolated `26.3-Fabric` instance and enter the disposable
   `AstralSky Phase0 Test` world.
3. Set clear weather and midnight. Confirm that Iris reports no shader compile
   error and that a structured, narrow Galactic band rotates with the stars.
4. Set `ASTRO_MILKY_WAY_BRIGHTNESS` to zero and back to its default to verify
   that only the diffuse Galactic band changes.
5. Compare the zenith and horizon. Stars and the Milky Way should fade more
   strongly near the horizon, while a subtle blue airglow remains.
6. Move beneath a fancy vanilla cloud and confirm that the cloud occludes the
   catalogue, bright-star points, Milky Way and airglow.
7. Run `/weather rain` and confirm that weather substantially fades the night
   sky without changing celestial orientation.

## Known scope

The Galactic coordinate plane and centre are astronomical. The fine Milky Way
brightness, dust and colour structure is procedural and display-oriented, not
an all-sky survey image. Solar/lunar ephemerides and lunar phases are scheduled
for the next milestone in `ROADMAP.md`.

## Runtime result

Validated on Minecraft 26.3 / Iris 1.11.7 on 2026-11-01:

- the shader compiled without an Iris error;
- the Galactic anti-centre direction showed the expected continuous faint band
  and dust modulation;
- stable rain reduced the catalogue and Milky Way to near invisibility;
- fancy clouds occluded stars, the Milky Way and airglow while sky remained
  visible through cloud gaps;
- the test player and clear weather were restored after validation.
