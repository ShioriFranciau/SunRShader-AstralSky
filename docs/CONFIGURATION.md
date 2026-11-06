# Configuration

The Fabric companion creates this file on first launch:

```text
<instance>/config/astralsky.json
```

For the isolated PCL2 instance used during development, the full path is:

```text
D:\PCL2\.minecraft\versions\26.3-Fabric\config\astralsky.json
```

Press `K` in game to open the observer editor. It is the single source of truth
for the time mode, latitude, longitude, UTC offset, fixed date/time and north
rotation. Numeric values provide a slider plus a text box for exact entry.
Fields that do not apply to the selected time mode are disabled. Saving writes
the configuration and asks Iris to rebuild its shader pipeline immediately;
`F3+T` is neither required nor sufficient for these compile-time values. The
key can be changed in Minecraft's Controls screen.

The JSON file can still be edited while Minecraft is closed. On startup, the
mod validates the values and writes them to the AstralSky shader pack before
Iris creates its rendering pipeline.

## Example

```json
{
  "schemaVersion": 2,
  "timeMode": "SYSTEM_REALTIME",
  "latitudeDegrees": 31.2304,
  "longitudeDegreesEast": 121.4737,
  "utcOffsetHours": 8.0,
  "fixedYear": 2026,
  "fixedMonth": 10,
  "fixedDay": 30,
  "fixedLocalHour": 22.0,
  "northOffsetDegrees": 0.0,
  "advancingStartWorldDay": 0,
  "advancingStartWorldTime": 0
}
```

## Time modes

- `SYSTEM_REALTIME`: use the computer's calendar date and wall-clock time.
  `utcOffsetHours` converts local time to UTC.
- `FIXED_DATETIME`: use `fixedYear`, `fixedMonth`, `fixedDay` and
  `fixedLocalHour`; `utcOffsetHours` converts the fixed local time to UTC.
- `FIXED_START_GAME_ELAPSED`: use the fixed date as the epoch date, then
  advance the sky with Minecraft world time (1000 ticks per astronomical
  hour, 24000 ticks per day). Saving this mode initializes the game clock to
  `fixedLocalHour` (tick 0 = 06:00, no solar-time correction) and captures
  that instant as the anchor, so the sky reads the same civil hour as the
  game clock and stays aligned with `/time set`. Pausing the world also
  pauses astronomical time. The game clock is only initialized on an
  integrated (single-player) server; a dedicated server keeps its current
  clock and only the anchor is captured.

Astronomical civil times are always converted with `utcOffsetHours`; no
apparent-solar-time conversion is applied anywhere.

`advancingStartWorldDay` and `advancingStartWorldTime` are managed by the `K`
screen and normally should not be edited manually.

Latitude is north-positive and longitude is east-positive. Valid ranges are
90 S to 90 N, 180 W to 180 E, and UTC-14 to UTC+14. Invalid dates and numeric
ranges are clamped to a valid value when the game starts.

`northOffsetDegrees` rotates the astronomical north direction around the
horizon. Leave it at zero for the normal Minecraft mapping where north is
negative Z.

The same selected date and time drive local sidereal rotation, the real solar
and lunar positions, lunar distance and the continuously calculated Moon
phase. Solar eclipses use topocentric lunar parallax, so the same instant can
produce totality inside the eclipse path and no eclipse at a distant location.
Iris' Astronomy screen also exposes catalogue size, faint-star strength,
star colour, Milky Way brightness, displayed Moon size and earthshine. The
catalogue selector offers BSC5, Hipparcos and the 16K Tycho-2 atlas; Tycho-2
uses more texture memory and is intended for systems that prioritize dense
faint-star fields. The lunar surface texture and its IAU body orientation are
date-driven and require no separate toggle.

The planets Mercury through Neptune are on by default. `ASTRO_PLANETS` switches
the whole group; `ASTRO_PLANET_BRIGHTNESS` scales the radiance, and
`ASTRO_PLANET_SIZE` scales the display radius, whose floor
(`ASTRO_PLANET_MIN_RADIUS`, 0.0007 rad) keeps a sub-pixel disc visible. At the
default brightness Uranus and Neptune are dimmer than the faint-star limit, just
as they are to the naked eye; raise the brightness to bring them out. Their
positions, phases, magnitudes and angular sizes are always calculated from the
selected date and time, and `docs/PLANETS.md` documents the VSOP87 pipeline.

Iris' Astronomy page intentionally contains only visual controls such as star
catalogue size, brightness, Milky Way appearance and Moon rendering. Observer
and time values are kept exclusively in the `K` editor to prevent two settings
screens from overwriting one another.

## Shader-pack formats

The companion updates either an unpacked `SunRShader-AstralSky` directory or
the selected packaged `.zip`. The ZIP must contain `shaders/` at its root, as
the Gradle build artifact does. Close Minecraft before replacing or editing a
packaged ZIP.

The generated configuration carries a schema marker that the pack validates:
a companion and a pack built from different generations ignore each other's
mapping and fall back to the pack defaults instead of silently activating the
wrong time mode. Update both together and restart the game after replacing
the companion JAR, which Minecraft locks while it runs.
