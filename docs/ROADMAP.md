# Roadmap

## Completed: night-sky environment

- BSC5P star catalogue plus independently projected bright stars;
- date-, clock- and location-dependent celestial rotation;
- IAU J2000 Galactic orientation with procedural Milky Way structure;
- altitude-dependent extinction, night airglow and vanilla-cloud compatibility.

## Current: Solar System sky

- low-order geocentric solar right ascension and declination for the selected
  observation date and time;
- lunar position with the dominant longitude, latitude and distance
  perturbations needed for visual use;
- physically oriented lunar terminator, distance-dependent apparent size and
  configurable earthshine;
- procedural Sun and Moon replace the vanilla celestial textures and remain
  occluded by terrain and fancy clouds;
- VSOP87D heliocentric series for Mercury through Neptune, giving geocentric
  right ascension, declination, distance, phase angle, illuminated fraction,
  apparent magnitude, angular radius and solar elongation;
- the planet table is evaluated once per frame in the deferred compute pass, so
  the per-pixel cost is seven texel fetches rather than 2,676 series terms;
- planets share the Sun/Moon equatorial-of-date frame and the same atmosphere,
  cloud and weather occlusion, with configurable brightness and display size;
- golden-reference tests cover the 2024-04-08 eclipse, the 2024-03-25 full
  Moon, the 2024-04-08 new Moon, the June solstice, the 2020-12-21
  Jupiter/Saturn conjunction and the 2025-01-16 Mars opposition;
- Iris/NVIDIA runtime validation covers shader compilation, a predicted
  Shanghai meridian Sun position and the 2024-04-15 first-quarter lunar limb;
- southern-hemisphere validation covers the opposite limb at the 2024-04-02
  last quarter for latitude -33.8688 degrees;
- the Moon display scale is configurable from 1x physical angular size to 5x,
  with 5x used by the default profile for phase readability.

## Next validation and polish

- validate planet positions in-game against a planetarium for a handful of dates
  around a conjunction and an opposition;
- validate lunar limb orientation in-game at a representative waning phase;
- validate rise/set direction and meridian transit in both hemispheres;
- add optional solar/lunar halo and exposure controls without washing out the
  stellar background;
- evaluate an optional high-resolution lunar albedo texture.

## Later accuracy and presentation

- precession, nutation and aberration, so the J2000 star atlas and the
  of-date Solar System can share one frame (currently about 0.36 degrees apart
  in 2026);
- light-time correction for the outer planets (at most about twenty
  arcseconds);
- Saturn ring tilt in the magnitude model (about one magnitude near a
  ring-plane crossing);
- stellar proper motion for high-motion nearby stars;
- optional atmospheric refraction close to the horizon;
- exposure controls and higher-detail Milky Way assets.
