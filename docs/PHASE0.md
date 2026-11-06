# Phase 0 verification

## Automated checks

Run:

```powershell
.\scripts\build.ps1
```

The build verifies:

- the canonical J2000 Julian date;
- the canonical J2000 Greenwich mean sidereal angle;
- parity between the double-precision reference and the GPU-safe split-day formula;
- the zenith transform invariant;
- Polaris altitude near the observer latitude for Shanghai.

## Iris runtime check

1. Deploy with `.\scripts\deploy-dev.ps1 -Build` while Minecraft is closed.
2. Start the isolated `26.3-Fabric` PCL2 instance.
3. Select `SunRShader-AstralSky` in Iris if it is not already selected.
4. Confirm that `logs/latest.log` contains `Using shaderpack: SunRShader-AstralSky`
   and no shader compile error.

The 26.3 render path uses small passthrough `gbuffers_*` programs so blocks,
items and the vanilla sky retain their normal colour and depth. The basic pass
discards only `MC_RENDER_STAGE_STARS`; this prevents the vanilla random star
mesh from being drawn behind the astronomical catalogue. The final pass then
projects catalogue directions through Iris' current model-view and projection
matrices and draws them only where the scene depth is sky.

## Visual check

Use a disposable test world rather than an existing save.

1. Set clear weather and local midnight.
2. Face north at the default Shanghai latitude. Polaris should remain close to
   31 degrees above the northern horizon as the game clock advances.
3. Face east before midnight and confirm that rising stars move westward over
   the night.
4. Confirm that terrain occludes stars and that rain fades them.
5. Switch the date/time mode and verify that star positions move while terrain
   and the vanilla sky remain unchanged.

### Reproducible Shanghai check

With the default profile on 2026-10-31, run:

```text
/time set midnight
/tp @s ~ ~ ~ -127.17 -52.76
```

The camera is three degrees below Capella's calculated position for longitude
121.47 E and latitude 31.23 N. Capella should therefore appear directly above
the crosshair. The same calculation puts Deneb at azimuth 310.41 degrees and
altitude 22.51 degrees. Runtime validation on Minecraft 26.3 / Iris 1.11.7
matched both bearings, preserved terrain occlusion, and showed no shader
compile errors.

Phase 0 used `clouds=off` to isolate star projection and terrain depth. Phase 1
restores fancy vanilla clouds and adds atmospheric/cloud compatibility checks.

## Phase 0 limitations

- Phase 0 rendered only 20 bright J2000 reference stars; Phase 1 adds the BSC5P
  catalogue and a correctly oriented procedural Milky Way.
- Proper motion, precession, nutation and atmospheric refraction remain
  deferred.
- Shader-pack sliders expose representative fallback values when the Fabric
  companion is absent. With the companion installed, arbitrary values are
  loaded from `config/astralsky.json` at startup.

