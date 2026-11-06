# Star catalogues

AstralSky ships three selectable J2000/ICRS star atlases:

- BSC5 lightweight mode, derived from the Bright Star Catalogue, 5th Revised
  Edition (Preliminary Version), catalogue `V/50`, rendered to a 4096 by 2048
  atlas;
- Hipparcos extended mode, from the official main catalogue `I/239`, rendered
  to an 8192 by 4096 atlas;
- Tycho-2 ultra mode, from the official main catalogue `I/259`, containing
  2,539,913 entries and rendered to a 16384 by 8192 atlas.

All use J2000 right ascension and declination plus Johnson visual magnitude
and B-V colour. The Hipparcos source contains 118,218 rows; 118,093 rows with
usable coordinates and photometry are rasterized, and 2,430,342 of Tycho-2's
2,539,913 entries pass the same filters. Generated textures are committed, so
builds and game startup need no network access.

## Regenerating the atlases

1. `scripts\generate-star-atlas.ps1` (optional; BSC5 dimensions are
   unchanged) — downloads BSC5P from HEASARC;
2. `scripts\generate-hipparcos-atlas.ps1` — downloads Hipparcos from HEASARC;
3. `scripts\generate-tycho2-atlas.ps1` — reuses the cached Tycho-2 bands in
   `build\catalog-source\tycho2-parts\` without network access when present.

Hipparcos must be regenerated before Tycho-2: the Tycho-2 render merges the
Hipparcos bright-star atlas and requires tycho2 to be exactly twice the
Hipparcos dimensions in both axes. The 16384 by 8192 Tycho-2 atlas also has a
transient video-memory footprint of roughly 1 GiB during the deferred stage
(RGBA16F); cards with limited VRAM can revert the tycho2 entry in
`tools/StarAtlasGenerator.java`'s `AtlasProfile` table to 8192 by 4096.

Official source descriptions:

```text
https://heasarc.gsfc.nasa.gov/W3Browse/catalog/bsc5p.html
https://heasarc.gsfc.nasa.gov/W3Browse/star-catalog/hipparcos.html
https://cdsarc.cds.unistra.fr/viz-bin/ReadMe/I/239
```

The shader applies the date/location sidereal transform, horizon fade,
night/weather visibility and twinkle at runtime. Automated atlas tests check
Capella, Sirius, Polaris, Canopus, Arcturus, Vega, Achernar and Acrux across
both celestial hemispheres and near the right-ascension seam.

Tycho-2 uses its epoch-J2000 mean ICRS coordinates, falling back to the
catalogue's observed ICRS coordinates for entries whose mean-position flag is
`X`. Johnson photometry is approximated from Tycho photometry using
`V = VT - 0.090(BT - VT)` and
`B-V = 0.850(BT - VT)`, as specified by the catalogue documentation. Its
larger atlas limits collisions in dense Galactic-plane fields while retaining
the point-source mask used for narrow-FOV rendering. A maximum-brightness
Hipparcos layer supplements bright and complex stars absent from the Tycho-2
main table; overlapping records are not additively brightened.

`ASTRO_CATALOG_MODE` selects BSC5, Hipparcos or Tycho-2. Catalogue brightness,
faint-star response, colour strength and twinkle are independently adjustable
in Iris' Astronomy screen.

Each atlas keeps its filtered RGB image for normal fields of view and stores a
sparse point-source mask in alpha. At narrow fields of view the shader uses the
mask to reconstruct sub-pixel stars, preventing the baked atlas kernel from
being magnified into a blurred disc.
