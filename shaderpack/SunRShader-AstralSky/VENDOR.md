# Vendoring record

`shaderpack/` is a fork of **Photon Shaders**. This file records exactly what was
taken, from where, and what has been changed, so the provenance is auditable.

## Upstream

| Field | Value |
| --- | --- |
| Project | Photon Shaders |
| Author | Benjamin Stott ("Sixthsurge") |
| Repository | https://github.com/sixthsurge/photon |
| Branch | `main` |
| Commit | `15458c0937f8647c37eb6a501bef5eb3bf3da31b` |
| Vendored on | 2026-09-12 |
| Licence | Photon Shaders License Agreement — retained verbatim at `LICENSE` |

## What was taken

| Path | Included | Note |
| --- | --- | --- |
| `shaders/` | yes | The shader pack itself, complete and unmodified apart from the tweaks listed below. |
| `LICENSE` | yes | Verbatim, as the licence requires ("must be included in all substantial copies"). |
| `README.md` | yes | Renamed to `UPSTREAM-README.md` so it does not shadow this directory's own README. The licence and feature list it documents still apply. |
| `.clang-format`, `.clang-format-ignore`, `.gitattributes` | yes | Kept so that edits to the shader sources stay style-consistent with upstream. |
| `docs/` | no | 5.3 MB of showcase art and a style guide with no bearing on this fork. |
| `.github/` | no | Upstream's issue templates and workflows do not apply to a fork. |
| `scripts/` | no | Upstream's own maintenance scripts (include-guard generation, slider-value generation). They only operate on upstream's tree layout. |

## What has been changed

This section is updated as the fork diverges. Every entry names the file and the
reason, so upstream can be re-merged or re-diffed later.

*(Nothing yet — Phase 3 of the development plan applies the first changes.)*

## Why this pack

Chosen over the alternatives on licence and capability grounds:

- The licence explicitly permits modification and redistribution of derivative
  works, with the sole restriction that derivatives are not published on
  platforms that monetarily compensate creators (Modrinth, CurseForge) without
  written permission. Most comparable packs (Bliss, BSL, Solas, Kappa, AstraLex,
  DrDesten) are all-rights-reserved or permission-gated.
- It already provides everything the non-signature half of this project needs:
  physically-based atmospheric scattering (`include/sky/atmosphere.glsl`),
  multi-layer volumetric clouds (`include/sky/clouds/`), cloud shadows, PCSS
  soft shadows, volumetric fog and full labPBR material support.
- It supports Iris 1.5+ and has an explicit `IS_IRIS` code path, so Minecraft
  1.20.1 with Iris 1.7.6 needs no compatibility work.
- Its sun direction is derived in `shaderpack/shaders/shaders.properties` from
  Iris's `sunPosition` / `moonPosition` uniforms via `gbufferModelViewInverse`,
  which is exactly the injection point this project needs. See
  `docs/01-iris-development-notes.md`.
